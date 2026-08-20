package com.educloud.common.id;

import java.time.Duration;
import java.util.Optional;

public interface WorkerLeaseRepository {

    Optional<WorkerLeaseGrant> tryAcquire(
            String environment,
            String ownerId,
            Duration leaseTtl);

    Optional<WorkerLeaseGrant> renew(
            String environment,
            int workerId,
            String ownerId,
            Duration leaseTtl,
            long lastIssuedTimestamp);

    boolean release(
            String environment,
            int workerId,
            String ownerId,
            long lastIssuedTimestamp);
}
