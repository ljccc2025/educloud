package com.educloud.common.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkerLeaseManagerTest {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration RENEWAL_INTERVAL = Duration.ofSeconds(10);

    @Test
    void acquiresAWorkerAndSchedulesRenewalAtTheConfiguredInterval() {
        var repository = new FakeRepository();
        repository.acquireWorker = 7;
        var fixture = fixture(repository, new AtomicLong(1_000));

        fixture.manager.start();

        assertThat(fixture.manager.isRunning()).isTrue();
        assertThat(fixture.manager.requireActiveWorkerId()).isEqualTo(7);
        assertThat(repository.environments).containsExactly("test");
        assertThat(repository.ttls).containsExactly(TTL);
        verify(fixture.scheduler).scheduleWithFixedDelay(
                any(Runnable.class),
                eq(RENEWAL_INTERVAL.toNanos()),
                eq(RENEWAL_INTERVAL.toNanos()),
                eq(TimeUnit.NANOSECONDS));
    }

    @Test
    void usesAUniqueUuidOwnerForEveryManagerInstance() {
        var firstRepository = new FakeRepository();
        var secondRepository = new FakeRepository();
        var first = new WorkerLeaseManager(
                firstRepository, "test", TTL, RENEWAL_INTERVAL);
        var second = new WorkerLeaseManager(
                secondRepository, "test", TTL, RENEWAL_INTERVAL);

        first.start();
        second.start();

        try {
            assertThat(firstRepository.ownerIds.get(0))
                    .isNotEqualTo(secondRepository.ownerIds.get(0));
            UUID.fromString(firstRepository.ownerIds.get(0));
            UUID.fromString(secondRepository.ownerIds.get(0));
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void failsStartupWhenNoValidWorkerSlotIsAvailable() {
        var noSlotRepository = new FakeRepository();
        noSlotRepository.acquireWorker = null;
        var noSlot = fixture(noSlotRepository, new AtomicLong());

        assertThatThrownBy(noSlot.manager::start)
                .isInstanceOf(IdentifierUnavailableException.class)
                .hasMessageContaining("worker lease");
        assertThat(noSlot.manager.isRunning()).isFalse();

        var invalidRepository = new FakeRepository();
        invalidRepository.acquireWorker = 32;
        var invalid = fixture(invalidRepository, new AtomicLong());
        assertThatThrownBy(invalid.manager::start)
                .isInstanceOf(IdentifierUnavailableException.class)
                .hasMessageContaining("worker id");
    }

    @Test
    void renewalExtendsTheMonotonicDeadline() {
        var nanoTime = new AtomicLong(100);
        var repository = new FakeRepository();
        var fixture = fixture(repository, nanoTime);
        fixture.manager.start();

        nanoTime.addAndGet(RENEWAL_INTERVAL.toNanos());
        fixture.runRenewal();
        nanoTime.addAndGet(TTL.toNanos() - 1);

        assertThat(fixture.manager.requireActiveWorkerId()).isZero();
        assertThat(repository.renewCalls).isEqualTo(1);
    }

    @Test
    void leaseDeadlinesIncludeRedisRoundTripTime() {
        var acquisitionNanos = new AtomicLong(100);
        var acquisitionRepository = new FakeRepository();
        acquisitionRepository.acquireAction =
                () -> acquisitionNanos.addAndGet(Duration.ofSeconds(5).toNanos());
        var acquisition = fixture(acquisitionRepository, acquisitionNanos);
        acquisition.manager.start();
        acquisitionNanos.set(100 + TTL.toNanos());

        assertThatThrownBy(acquisition.manager::requireActiveWorkerId)
                .isInstanceOf(IdentifierUnavailableException.class)
                .hasMessageContaining("expired");

        var renewalNanos = new AtomicLong(100);
        var renewalRepository = new FakeRepository();
        var renewal = fixture(renewalRepository, renewalNanos);
        renewal.manager.start();
        renewalNanos.set(100 + RENEWAL_INTERVAL.toNanos());
        long renewalStarted = renewalNanos.get();
        renewalRepository.renewAction =
                () -> renewalNanos.addAndGet(Duration.ofSeconds(5).toNanos());
        renewal.runRenewal();
        renewalNanos.set(renewalStarted + TTL.toNanos());

        assertThatThrownBy(renewal.manager::requireActiveWorkerId)
                .isInstanceOf(IdentifierUnavailableException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void failsClosedOnEmptyRenewalRepositoryFailureOrOwnershipChange() {
        assertRenewalFailureCloses(repository -> repository.renewMode = RenewMode.EMPTY);
        assertRenewalFailureCloses(repository -> repository.renewMode = RenewMode.THROW);
        assertRenewalFailureCloses(repository -> repository.renewMode = RenewMode.WRONG_OWNER);
    }

    @Test
    void checksTheMonotonicDeadlineBeforeEveryIdentifier() {
        var nanoTime = new AtomicLong(1_000);
        var fixture = fixture(new FakeRepository(), nanoTime);

        assertThatThrownBy(fixture.manager::requireActiveWorkerId)
                .isInstanceOf(IdentifierUnavailableException.class);

        fixture.manager.start();
        assertThat(fixture.manager.requireActiveWorkerId()).isZero();

        nanoTime.addAndGet(TTL.toNanos());
        assertThatThrownBy(fixture.manager::requireActiveWorkerId)
                .isInstanceOf(IdentifierUnavailableException.class)
                .hasMessageContaining("expired");
        assertThatThrownBy(fixture.manager::requireActiveWorkerId)
                .isInstanceOf(IdentifierUnavailableException.class);
    }

    @Test
    void stopsLocallyAndReleasesWithTheMaximumIssuedTimestamp() {
        var repository = new FakeRepository();
        var fixture = fixture(repository, new AtomicLong());
        fixture.manager.start();
        fixture.manager.recordIssuedTimestamp(500);
        fixture.manager.recordIssuedTimestamp(300);

        fixture.manager.stop();

        assertThat(fixture.manager.isRunning()).isFalse();
        assertThat(repository.releasedWorker).isZero();
        assertThat(repository.releasedTimestamp).isEqualTo(500);
        assertThat(repository.releasedOwner).isEqualTo(repository.ownerIds.get(0));
        assertThatThrownBy(fixture.manager::requireActiveWorkerId)
                .isInstanceOf(IdentifierUnavailableException.class);
    }

    private static void assertRenewalFailureCloses(
            java.util.function.Consumer<FakeRepository> failure) {
        var repository = new FakeRepository();
        var fixture = fixture(repository, new AtomicLong());
        fixture.manager.start();
        failure.accept(repository);

        fixture.runRenewal();

        assertThat(fixture.manager.isRunning()).isFalse();
        assertThatThrownBy(fixture.manager::requireActiveWorkerId)
                .isInstanceOf(IdentifierUnavailableException.class);
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(FakeRepository repository, AtomicLong nanoTime) {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);
        when(scheduler.scheduleWithFixedDelay(
                        any(Runnable.class),
                        any(Long.class),
                        any(Long.class),
                        any(TimeUnit.class)))
                .thenReturn((ScheduledFuture) future);
        var manager = new WorkerLeaseManager(
                repository,
                "test",
                TTL,
                RENEWAL_INTERVAL,
                UUID.randomUUID().toString(),
                nanoTime::get,
                scheduler);
        return new Fixture(manager, scheduler);
    }

    private record Fixture(
            WorkerLeaseManager manager,
            ScheduledExecutorService scheduler) {

        private void runRenewal() {
            var runnable = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).scheduleWithFixedDelay(
                    runnable.capture(),
                    eq(RENEWAL_INTERVAL.toNanos()),
                    eq(RENEWAL_INTERVAL.toNanos()),
                    eq(TimeUnit.NANOSECONDS));
            runnable.getValue().run();
        }
    }

    private enum RenewMode {
        SUCCESS,
        EMPTY,
        THROW,
        WRONG_OWNER
    }

    private static final class FakeRepository implements WorkerLeaseRepository {
        private Integer acquireWorker = 0;
        private RenewMode renewMode = RenewMode.SUCCESS;
        private Runnable acquireAction = () -> {};
        private Runnable renewAction = () -> {};
        private int renewCalls;
        private final List<String> environments = new ArrayList<>();
        private final List<String> ownerIds = new ArrayList<>();
        private final List<Duration> ttls = new ArrayList<>();
        private Integer releasedWorker;
        private String releasedOwner;
        private long releasedTimestamp;

        @Override
        public Optional<WorkerLeaseGrant> tryAcquire(
                String environment,
                String ownerId,
                Duration leaseTtl) {
            environments.add(environment);
            ownerIds.add(ownerId);
            ttls.add(leaseTtl);
            acquireAction.run();
            return acquireWorker == null
                    ? Optional.empty()
                    : Optional.of(new WorkerLeaseGrant(acquireWorker, ownerId, 1_000));
        }

        @Override
        public Optional<WorkerLeaseGrant> renew(
                String environment,
                int workerId,
                String ownerId,
                Duration leaseTtl,
                long lastIssuedTimestamp) {
            renewCalls++;
            renewAction.run();
            return switch (renewMode) {
                case SUCCESS -> Optional.of(new WorkerLeaseGrant(workerId, ownerId, 2_000));
                case EMPTY -> Optional.empty();
                case THROW -> throw new IdentifierUnavailableException("redis unavailable");
                case WRONG_OWNER -> Optional.of(
                        new WorkerLeaseGrant(workerId, UUID.randomUUID().toString(), 2_000));
            };
        }

        @Override
        public boolean release(
                String environment,
                int workerId,
                String ownerId,
                long lastIssuedTimestamp) {
            releasedWorker = workerId;
            releasedOwner = ownerId;
            releasedTimestamp = lastIssuedTimestamp;
            return true;
        }
    }
}
