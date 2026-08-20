package com.educloud.common.id;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/** Owns one Redis-backed worker lease and fails closed whenever ownership is uncertain. */
public final class WorkerLeaseManager implements WorkerLeaseGuard, SmartLifecycle, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerLeaseManager.class);

    private final WorkerLeaseRepository repository;
    private final String environment;
    private final Duration leaseTtl;
    private final Duration renewalInterval;
    private final String ownerId;
    private final LongSupplier nanoTime;
    private final ScheduledExecutorService scheduler;
    private final Object lifecycleMonitor = new Object();
    private final AtomicLong lastIssuedTimestamp = new AtomicLong();

    private volatile LeaseState state = LeaseState.NEW;
    private volatile int workerId = -1;
    private volatile long confirmedUntilNanos;
    private ScheduledFuture<?> renewalTask;

    public WorkerLeaseManager(
            WorkerLeaseRepository repository,
            String environment,
            Duration leaseTtl,
            Duration renewalInterval) {
        this(
                repository,
                environment,
                leaseTtl,
                renewalInterval,
                UUID.randomUUID().toString(),
                System::nanoTime,
                newDaemonScheduler());
    }

    WorkerLeaseManager(
            WorkerLeaseRepository repository,
            String environment,
            Duration leaseTtl,
            Duration renewalInterval,
            String ownerId,
            LongSupplier nanoTime,
            ScheduledExecutorService scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.environment = requireEnvironment(environment);
        this.leaseTtl = requirePositive(leaseTtl, "leaseTtl");
        this.renewalInterval = requirePositive(renewalInterval, "renewalInterval");
        if (this.renewalInterval.compareTo(this.leaseTtl) >= 0) {
            throw new IllegalArgumentException("renewalInterval must be less than leaseTtl");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        this.ownerId = ownerId;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void start() {
        synchronized (lifecycleMonitor) {
            if (state == LeaseState.ACTIVE) {
                return;
            }
            if (state != LeaseState.NEW) {
                throw new IdentifierUnavailableException("worker lease manager cannot be restarted");
            }

            WorkerLeaseGrant grant;
            try {
                grant = repository.tryAcquire(environment, ownerId, leaseTtl)
                        .orElseThrow(() -> new IdentifierUnavailableException(
                                "no worker lease slot is available"));
                validateGrant(grant, null);
            } catch (RuntimeException exception) {
                state = LeaseState.INACTIVE;
                scheduler.shutdownNow();
                throw unavailable("worker lease acquisition failed", exception);
            }

            workerId = grant.workerId();
            confirmedUntilNanos = nanoTime.getAsLong() + leaseTtl.toNanos();
            state = LeaseState.ACTIVE;
            try {
                renewalTask = scheduler.scheduleWithFixedDelay(
                        this::renewSafely,
                        renewalInterval.toNanos(),
                        renewalInterval.toNanos(),
                        TimeUnit.NANOSECONDS);
            } catch (RuntimeException exception) {
                state = LeaseState.INACTIVE;
                tryReleaseAfterFailedStart(grant.workerId());
                throw unavailable("worker lease renewal scheduling failed", exception);
            }
        }
    }

    @Override
    public int requireActiveWorkerId() {
        if (state != LeaseState.ACTIVE) {
            throw new IdentifierUnavailableException("worker lease is inactive");
        }
        long now = nanoTime.getAsLong();
        if (now - confirmedUntilNanos >= 0) {
            invalidateLocally();
            throw new IdentifierUnavailableException("worker lease confirmation expired");
        }
        return workerId;
    }

    @Override
    public void recordIssuedTimestamp(long epochMillis) {
        lastIssuedTimestamp.accumulateAndGet(epochMillis, Math::max);
    }

    @Override
    public void stop() {
        int workerToRelease;
        long watermark;
        synchronized (lifecycleMonitor) {
            if (state != LeaseState.ACTIVE) {
                state = LeaseState.STOPPED;
                cancelRenewal();
                scheduler.shutdownNow();
                return;
            }
            workerToRelease = workerId;
            watermark = lastIssuedTimestamp.get();
            state = LeaseState.STOPPED;
            confirmedUntilNanos = 0;
            cancelRenewal();
        }

        try {
            if (!repository.release(environment, workerToRelease, ownerId, watermark)) {
                LOGGER.warn(
                        "Worker lease release was rejected for environment {} and worker {}",
                        environment,
                        workerToRelease);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Worker lease release failed for environment {} and worker {}",
                    environment,
                    workerToRelease,
                    exception);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        if (state != LeaseState.ACTIVE) {
            return false;
        }
        if (nanoTime.getAsLong() - confirmedUntilNanos >= 0) {
            invalidateLocally();
            return false;
        }
        return true;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    @Override
    public void close() {
        stop();
    }

    private void renewSafely() {
        int leasedWorker;
        synchronized (lifecycleMonitor) {
            if (state != LeaseState.ACTIVE) {
                return;
            }
            leasedWorker = workerId;
        }

        WorkerLeaseGrant renewed;
        try {
            renewed = repository.renew(
                            environment,
                            leasedWorker,
                            ownerId,
                            leaseTtl,
                            lastIssuedTimestamp.get())
                    .orElseThrow(() -> new IdentifierUnavailableException(
                            "worker lease renewal was rejected"));
            validateGrant(renewed, leasedWorker);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Worker lease renewal failed; identifier generation is now disabled for environment {}",
                    environment,
                    exception);
            invalidateLocally();
            return;
        }

        synchronized (lifecycleMonitor) {
            if (state == LeaseState.ACTIVE && workerId == leasedWorker) {
                confirmedUntilNanos = nanoTime.getAsLong() + leaseTtl.toNanos();
            }
        }
    }

    private void validateGrant(WorkerLeaseGrant grant, Integer expectedWorkerId) {
        if (grant.workerId() < 0 || grant.workerId() > 31) {
            throw new IdentifierUnavailableException("worker id must be between 0 and 31");
        }
        if (!ownerId.equals(grant.ownerId())) {
            throw new IdentifierUnavailableException("worker lease owner changed unexpectedly");
        }
        if (expectedWorkerId != null && grant.workerId() != expectedWorkerId) {
            throw new IdentifierUnavailableException("worker lease id changed unexpectedly");
        }
    }

    private void invalidateLocally() {
        synchronized (lifecycleMonitor) {
            if (state == LeaseState.ACTIVE) {
                state = LeaseState.INACTIVE;
                confirmedUntilNanos = 0;
                cancelRenewal();
            }
        }
    }

    private void cancelRenewal() {
        if (renewalTask != null) {
            renewalTask.cancel(false);
            renewalTask = null;
        }
    }

    private void tryReleaseAfterFailedStart(int acquiredWorkerId) {
        try {
            repository.release(
                    environment,
                    acquiredWorkerId,
                    ownerId,
                    lastIssuedTimestamp.get());
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to release worker lease after scheduler startup failure", exception);
        }
        scheduler.shutdownNow();
    }

    private static IdentifierUnavailableException unavailable(
            String message,
            RuntimeException cause) {
        if (cause instanceof IdentifierUnavailableException unavailable) {
            return unavailable;
        }
        return new IdentifierUnavailableException(message, cause);
    }

    private static Duration requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        try {
            duration.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " is too large", exception);
        }
        return duration;
    }

    private static String requireEnvironment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(
                    "environment must contain only letters, digits, dot, underscore or hyphen");
        }
        return value;
    }

    private static ScheduledExecutorService newDaemonScheduler() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "educloud-worker-lease-renewal");
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadScheduledExecutor(factory);
    }

    private enum LeaseState {
        NEW,
        ACTIVE,
        INACTIVE,
        STOPPED
    }
}
