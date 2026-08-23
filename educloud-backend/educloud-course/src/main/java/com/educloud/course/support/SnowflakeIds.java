package com.educloud.course.support;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;

/**
 * Snowflake ID 解析工具（M05 任务 8，质量审查收敛为公共工具）。
 *
 * <p>规格 §6：所有 Snowflake ID 在 DTO 一律 String（前端禁止 Number()，雪花 63 位
 * &gt; 2^53）；service 层用 Long.parseLong 解析。Bean Validation @Pattern("\\d{1,19}")
 * 已挡住非数字与超长，本类为防御性兜底：非数字或 19 位但超出 Long 范围 → 400
 * VALIDATION_FAILED；null 输入返回 null（可空字段，如 coverFileId）。</p>
 */
public final class SnowflakeIds {

    private SnowflakeIds() {
    }

    /** 解析 Snowflake ID 字符串为 Long；null → null；非数字/越界 → 400 VALIDATION_FAILED。 */
    public static Long parse(String raw, String field) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED,
                    field + " must be a numeric Snowflake ID: " + raw);
        }
    }
}
