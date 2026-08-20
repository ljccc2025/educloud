package com.educloud.common.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkerLeaseIdentifierGeneratorTest {

    private static final long EPOCH_MILLIS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
    private static final long SEQUENCE_MASK = (1L << 17) - 1;

    @Test
    void encodesTimestampWorkerAndSequenceInAPositiveLong() {
        var clock = new MutableClock(EPOCH_MILLIS + 1234);
        var guard = new FakeLeaseGuard(7);
        var generator = generator(clock, guard, clock::advance);

        long id = generator.nextId();

        assertThat(id).isPositive();
        assertThat(id >>> 22).isEqualTo(1234);
        assertThat((id >>> 17) & 31).isEqualTo(7);
        assertThat(id & SEQUENCE_MASK).isZero();
        assertThat(guard.lastIssuedTimestamp).isEqualTo(EPOCH_MILLIS + 1234);
    }

    @Test
    void incrementsWithinAMillisecondAndResetsAcrossMilliseconds() {
        var clock = new MutableClock(EPOCH_MILLIS + 10);
        var generator = generator(clock, new FakeLeaseGuard(1), clock::advance);

        long first = generator.nextId();
        long second = generator.nextId();
        clock.advance(Duration.ofMillis(1));
        long third = generator.nextId();

        assertThat(first & SEQUENCE_MASK).isZero();
        assertThat(second & SEQUENCE_MASK).isEqualTo(1);
        assertThat(third & SEQUENCE_MASK).isZero();
    }

    @Test
    void waitsForTheNextMillisecondWhenTheSequenceIsExhausted() {
        var clock = new MutableClock(EPOCH_MILLIS + 20);
        var sleeps = new ArrayList<Duration>();
        var generator = generator(clock, new FakeLeaseGuard(2), duration -> {
            sleeps.add(duration);
            clock.advance(duration);
        });

        for (long sequence = 0; sequence <= SEQUENCE_MASK; sequence++) {
            long id = generator.nextId();
            assertThat(id & SEQUENCE_MASK).isEqualTo(sequence);
        }
        long nextMillisecond = generator.nextId();

        assertThat(nextMillisecond & SEQUENCE_MASK).isZero();
        assertThat(nextMillisecond >>> 22).isEqualTo(21);
        assertThat(sleeps).containsExactly(Duration.ofMillis(1));
    }

    @Test
    void waitsForASmallClockRollback() {
        var clock = new MutableClock(EPOCH_MILLIS + 100);
        var sleeps = new ArrayList<Duration>();
        var generator = generator(clock, new FakeLeaseGuard(3), duration -> {
            sleeps.add(duration);
            clock.advance(duration);
        });
        generator.nextId();
        clock.setMillis(EPOCH_MILLIS + 98);

        long recovered = generator.nextId();

        assertThat(sleeps).containsExactly(Duration.ofMillis(2));
        assertThat(recovered >>> 22).isEqualTo(100);
        assertThat(recovered & SEQUENCE_MASK).isEqualTo(1);
    }

    @Test
    void permanentlyFailsClosedAfterALargeClockRollback() {
        var clock = new MutableClock(EPOCH_MILLIS + 100);
        var generator = generator(clock, new FakeLeaseGuard(3), clock::advance);
        generator.nextId();
        clock.setMillis(EPOCH_MILLIS + 94);

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IdentifierUnavailableException.class)
                .hasMessageContaining("rollback");

        clock.setMillis(EPOCH_MILLIS + 101);
        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IdentifierUnavailableException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void checksTheLeaseForEveryGenerationAndRejectsInvalidWorkers() {
        var clock = new MutableClock(EPOCH_MILLIS + 1);
        var guard = new FakeLeaseGuard(31);
        var generator = generator(clock, guard, clock::advance);

        generator.nextId();
        generator.nextId();
        assertThat(guard.requireCalls).isGreaterThanOrEqualTo(2);

        guard.active = false;
        assertThatThrownBy(generator::nextId).isInstanceOf(IdentifierUnavailableException.class);

        assertThatThrownBy(() -> generator(clock, new FakeLeaseGuard(-1), clock::advance).nextId())
                .isInstanceOf(IdentifierUnavailableException.class);
        assertThatThrownBy(() -> generator(clock, new FakeLeaseGuard(32), clock::advance).nextId())
                .isInstanceOf(IdentifierUnavailableException.class);
    }

    private static WorkerLeaseIdentifierGenerator generator(
            Clock clock,
            WorkerLeaseGuard guard,
            Sleeper sleeper) {
        return new WorkerLeaseIdentifierGenerator(
                guard,
                clock,
                sleeper,
                Duration.ofMillis(5));
    }

    private static final class FakeLeaseGuard implements WorkerLeaseGuard {
        private final int workerId;
        private boolean active = true;
        private int requireCalls;
        private long lastIssuedTimestamp = -1;

        private FakeLeaseGuard(int workerId) {
            this.workerId = workerId;
        }

        @Override
        public int requireActiveWorkerId() {
            requireCalls++;
            if (!active) {
                throw new IdentifierUnavailableException("lease is inactive");
            }
            return workerId;
        }

        @Override
        public void recordIssuedTimestamp(long epochMillis) {
            lastIssuedTimestamp = Math.max(lastIssuedTimestamp, epochMillis);
        }
    }

    private static final class MutableClock extends Clock {
        private long currentMillis;

        private MutableClock(long currentMillis) {
            this.currentMillis = currentMillis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("only UTC is supported by this test clock");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(currentMillis);
        }

        private void advance(Duration duration) {
            currentMillis += duration.toMillis();
        }

        private void setMillis(long value) {
            currentMillis = value;
        }
    }
}
