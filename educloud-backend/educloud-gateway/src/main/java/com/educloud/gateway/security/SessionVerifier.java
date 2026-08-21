package com.educloud.gateway.security;

import reactor.core.publisher.Mono;

public interface SessionVerifier {

    Mono<SessionCheckResult> verify(String subject, String sessionId, long tokenVersion);
}
