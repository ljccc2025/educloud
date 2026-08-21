package com.educloud.user.session;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 会话读模型存储（Gateway 消费侧契约）。
 * 依据：M03 设计规格第 4.1 节：key=educloud:{environment:auth}:session:{sid}，
 * hash 字段 subject/status/tokenVersion，TTL 必设且大于 0。
 */
public interface SessionStore {

    void writeActive(String sessionId, String subject, long tokenVersion, Duration ttl);

    void markRevoked(String sessionId, Duration ttl);

    /** 读取会话快照；键缺失返回 empty（Gateway 语义：MISSING → 401）。 */
    Optional<SessionSnapshot> read(String sessionId);

    record SessionSnapshot(String status, long tokenVersion) {
    }
}
