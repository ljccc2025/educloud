package com.educloud.order.service;

public interface IdempotencyService {

    String generateToken(Long userId);

    void validateAndConsume(Long userId, String token);
}
