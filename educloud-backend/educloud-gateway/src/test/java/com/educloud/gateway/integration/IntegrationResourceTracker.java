package com.educloud.gateway.integration;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

final class IntegrationResourceTracker implements AutoCloseable {

    private final Deque<ResourceCleanup> cleanups = new ArrayDeque<>();

    void track(String resourceId, CheckedCleanup cleanup) {
        cleanups.push(new ResourceCleanup(
                Objects.requireNonNull(resourceId, "resourceId"),
                Objects.requireNonNull(cleanup, "cleanup")));
    }

    @Override
    public void close() {
        AssertionError aggregate = null;
        while (!cleanups.isEmpty()) {
            ResourceCleanup resource = cleanups.pop();
            try {
                resource.cleanup().run();
            } catch (Throwable failure) {
                AssertionError wrapped = new AssertionError(
                        "integration cleanup failed for " + resource.resourceId(), failure);
                if (aggregate == null) {
                    aggregate = new AssertionError("one or more integration resources were not cleaned");
                }
                aggregate.addSuppressed(wrapped);
            }
        }
        if (aggregate != null) {
            throw aggregate;
        }
    }

    @FunctionalInterface
    interface CheckedCleanup {
        void run() throws Exception;
    }

    private record ResourceCleanup(String resourceId, CheckedCleanup cleanup) {
    }
}
