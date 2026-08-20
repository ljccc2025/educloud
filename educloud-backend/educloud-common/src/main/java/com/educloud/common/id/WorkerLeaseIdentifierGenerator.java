package com.educloud.common.id;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Generates 63-bit positive identifiers with a 41-bit timestamp, 5-bit worker and
 * 17-bit per-millisecond sequence.
 */
public final class WorkerLeaseIdentifierGenerator implements IdentifierGenerator {

    static final long EPOCH_MILLIS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

    static final int SEQUENCE_BITS = 17;
    static final int WORKER_BITS = 5;
    static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    static final int WORKER_SHIFT = SEQUENCE_BITS;
    static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS;

    private static final long WORKER_MASK = (1L << WORKER_BITS) - 1;
    private static final long TIMESTAMP_MASK = (1L << 41) - 1;

    private final WorkerLeaseGuard leaseGuard;
    private final Clock clock;
    private final Sleeper sleeper;
    private final long backwardToleranceMillis;

    private long lastTimestamp = -1;
    private long sequence;
    private boolean permanentlyUnavailable;

    public WorkerLeaseIdentifierGenerator(
            WorkerLeaseGuard leaseGuard,
            Clock clock,
            Sleeper sleeper,
            Duration backwardTolerance) {
        this.leaseGuard = Objects.requireNonNull(leaseGuard, "leaseGuard");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        Objects.requireNonNull(backwardTolerance, "backwardTolerance");
        if (backwardTolerance.isNegative()) {
            throw new IllegalArgumentException("backwardTolerance must not be negative");
        }
        this.backwardToleranceMillis = backwardTolerance.toMillis();
    }

    @Override
    public synchronized long nextId() {
        requireAvailable();
        int workerId = requireValidWorker();
        long currentTimestamp = clock.millis();

        if (lastTimestamp >= 0 && currentTimestamp < lastTimestamp) {
            currentTimestamp = recoverFromSmallRollback(currentTimestamp);
            workerId = requireSameWorker(workerId);
        }

        if (currentTimestamp == lastTimestamp && sequence == MAX_SEQUENCE) {
            currentTimestamp = waitForNextMillisecond(currentTimestamp);
            workerId = requireSameWorker(workerId);
        }

        long timestampPart = validateAndGetTimestampPart(currentTimestamp);
        sequence = currentTimestamp == lastTimestamp ? sequence + 1 : 0;
        lastTimestamp = currentTimestamp;
        leaseGuard.recordIssuedTimestamp(currentTimestamp);

        return (timestampPart << TIMESTAMP_SHIFT)
                | ((long) workerId << WORKER_SHIFT)
                | sequence;
    }

    private void requireAvailable() {
        if (permanentlyUnavailable) {
            throw new IdentifierUnavailableException("identifier generator is permanently unavailable");
        }
    }

    private int requireValidWorker() {
        int workerId = leaseGuard.requireActiveWorkerId();
        if (workerId < 0 || workerId > WORKER_MASK) {
            throw new IdentifierUnavailableException("worker id must be between 0 and " + WORKER_MASK);
        }
        return workerId;
    }

    private int requireSameWorker(int expectedWorkerId) {
        int activeWorkerId = requireValidWorker();
        if (activeWorkerId != expectedWorkerId) {
            throw failPermanently("worker lease changed while generating an identifier");
        }
        return activeWorkerId;
    }

    private long recoverFromSmallRollback(long currentTimestamp) {
        long rollbackMillis = lastTimestamp - currentTimestamp;
        if (rollbackMillis > backwardToleranceMillis) {
            throw failPermanently("clock rollback exceeded the configured tolerance");
        }
        sleep(Duration.ofMillis(rollbackMillis));
        long recoveredTimestamp = clock.millis();
        if (recoveredTimestamp < lastTimestamp) {
            throw failPermanently("clock did not recover from rollback within the configured tolerance");
        }
        return recoveredTimestamp;
    }

    private long waitForNextMillisecond(long currentTimestamp) {
        long waitMillis = Math.max(1, lastTimestamp + 1 - currentTimestamp);
        sleep(Duration.ofMillis(waitMillis));
        long advancedTimestamp = clock.millis();
        if (advancedTimestamp <= lastTimestamp) {
            throw failPermanently("clock did not advance after identifier sequence exhaustion");
        }
        return advancedTimestamp;
    }

    private void sleep(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            permanentlyUnavailable = true;
            throw new IdentifierUnavailableException(
                    "identifier generation was interrupted and is now unavailable",
                    exception);
        }
    }

    private long validateAndGetTimestampPart(long currentTimestamp) {
        long timestampPart = currentTimestamp - EPOCH_MILLIS;
        if (timestampPart < 0) {
            throw failPermanently("clock is before the identifier epoch");
        }
        if (timestampPart > TIMESTAMP_MASK) {
            throw failPermanently("identifier timestamp range is exhausted");
        }
        return timestampPart;
    }

    private IdentifierUnavailableException failPermanently(String message) {
        permanentlyUnavailable = true;
        return new IdentifierUnavailableException(message);
    }
}
