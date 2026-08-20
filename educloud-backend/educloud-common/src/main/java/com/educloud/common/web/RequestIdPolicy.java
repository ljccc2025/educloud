package com.educloud.common.web;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class RequestIdPolicy {

    private static final Pattern ACCEPTED_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final Supplier<UUID> uuidSupplier;

    public RequestIdPolicy(Supplier<UUID> uuidSupplier) {
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier, "uuidSupplier");
    }

    public String resolve(String candidate) {
        if (candidate != null && ACCEPTED_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return Objects.requireNonNull(uuidSupplier.get(), "generated requestId").toString();
    }
}
