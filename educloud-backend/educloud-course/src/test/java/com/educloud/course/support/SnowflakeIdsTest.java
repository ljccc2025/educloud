package com.educloud.course.support;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M05 任务 8 质量审查：Snowflake ID 公共解析工具单测。
 * 规格 §6：DTO 全 String；service 层 Long.parseLong 解析；非数字/越界 → 400。
 */
class SnowflakeIdsTest {

    @Test
    void parseReturnsLongForNumericString() {
        assertThat(SnowflakeIds.parse("123", "categoryId")).isEqualTo(123L);
        assertThat(SnowflakeIds.parse("9223372036854775807", "categoryId")).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void parseReturnsNullForNullInput() {
        assertThat(SnowflakeIds.parse(null, "coverFileId")).isNull();
    }

    @Test
    void parseRejectsNonNumericWith400() {
        assertThatThrownBy(() -> SnowflakeIds.parse("12a", "categoryId"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(400);
                });
    }

    @Test
    void parseRejectsOverflowBeyondLongRangeWith400() {
        // 19 位但 > Long.MAX_VALUE：@Pattern 可通过，Long.parseLong 拒绝 → 400 兜底。
        assertThatThrownBy(() -> SnowflakeIds.parse("9999999999999999999", "categoryId"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED));
    }
}
