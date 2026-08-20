package com.educloud.common.web;

import java.util.Optional;

public interface RequestContextAccessor {

    String requestId();

    Optional<String> traceId();
}
