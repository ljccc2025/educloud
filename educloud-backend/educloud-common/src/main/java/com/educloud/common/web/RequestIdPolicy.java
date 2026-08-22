package com.educloud.common.web;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class RequestIdPolicy {

    /** 与审计表 login_audit/audit_event/outbox_event 的 request_id VARCHAR(36) 对齐。 */
    private static final int MAX_REQUEST_ID_LENGTH = 36;
    /** 字符白名单；长度上限 64（超过视为非法改由服务端生成），合法值再截断到 36。 */
    private static final Pattern ACCEPTED_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]+");

    private final Supplier<UUID> uuidSupplier;

    public RequestIdPolicy(Supplier<UUID> uuidSupplier) {
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier, "uuidSupplier");
    }

    public String resolve(String candidate) {
        if (candidate != null
                && candidate.length() <= 64
                && ACCEPTED_REQUEST_ID.matcher(candidate).matches()) {
            // 合法但超长的客户端 ID 截断到 36，避免击穿审计表 VARCHAR(36) 列导致业务 500。
            return candidate.length() > MAX_REQUEST_ID_LENGTH
                    ? candidate.substring(0, MAX_REQUEST_ID_LENGTH)
                    : candidate;
        }
        return Objects.requireNonNull(uuidSupplier.get(), "generated requestId").toString();
    }
}
