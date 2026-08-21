package com.educloud.user.service;

import com.educloud.common.error.BusinessException;
import com.educloud.user.entity.IdempotencyRecordEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.IdempotencyRecordMapper;
import com.educloud.user.session.SessionFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * HTTP 幂等记录（idempotency_record）。同一键+相同请求重放返回首次结果；
 * 同一键但请求摘要不同返回 409（API 规范第 6 节；匿名注册 user_id 用 0 约定）。
 */
@Component
public class IdempotencyService {

    private final IdempotencyRecordMapper mapper;

    public IdempotencyService(IdempotencyRecordMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Transactional
    public Optional<StoredResponse> findReplay(
            String operation, String idempotencyKey, long userId, String requestHash) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        IdempotencyRecordEntity existing = mapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<IdempotencyRecordEntity>()
                        .eq("user_id", userId)
                        .eq("operation", operation)
                        .eq("idempotency_key_hash",
                                SessionFactory.sha256Hex(idempotencyKey)));
        if (existing == null) {
            return Optional.empty();
        }
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BusinessException(
                    UserErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used with a different request");
        }
        return Optional.of(new StoredResponse(
                existing.getResponseStatus(), existing.getResponseBodyJson()));
    }

    @Transactional
    public void record(
            String operation, String idempotencyKey, long userId, String requestHash,
            int responseStatus, String responseBodyJson) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        IdempotencyRecordEntity record = new IdempotencyRecordEntity();
        record.setUserId(userId);
        record.setOperation(operation);
        record.setIdempotencyKeyHash(SessionFactory.sha256Hex(idempotencyKey));
        record.setRequestHash(requestHash);
        record.setResponseStatus(responseStatus);
        record.setResponseBodyJson(responseBodyJson);
        record.setExpiresAt(Instant.now().plus(java.time.Duration.ofHours(24)));
        record.setCreatedAt(Instant.now());
        try {
            mapper.insert(record);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            // 并发双写：已存在即视为首次结果已落库，由调用方按成功路径处理。
            throw new BusinessException(
                    UserErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used concurrently");
        }
    }

    public record StoredResponse(int status, String bodyJson) {
    }
}
