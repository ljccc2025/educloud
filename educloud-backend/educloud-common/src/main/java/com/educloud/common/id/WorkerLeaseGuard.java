package com.educloud.common.id;

/** Guards identifier generation with an active, exclusively owned worker lease. */
public interface WorkerLeaseGuard {

    int requireActiveWorkerId();

    void recordIssuedTimestamp(long epochMillis);
}
