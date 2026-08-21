package com.educloud.user.dto.response;

import java.time.Instant;

/**
 * JWT 签名公钥状态（不含私钥；安全设计第 9 节）。
 * nextRotationAt 在真实轮换流程落地前为 null（TODO：生产轮换计划）。
 */
public record SigningKeyStatusResponse(
        String activeKid,
        int keyCount,
        Instant updatedAt,
        Instant nextRotationAt) {
}
