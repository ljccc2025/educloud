package com.educloud.common.id;

public record WorkerLeaseGrant(
        int workerId,
        String ownerId,
        long redisTimeMillis) {}
