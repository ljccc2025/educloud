package com.educloud.analytics.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum RebuildStatus {
    RUNNING("RUNNING", "执行中"),
    SUCCESS("SUCCESS", "重算成功"),
    FAILED("FAILED", "执行失败");

    @EnumValue
    @JsonValue
    private final String code;
    private final String description;

    RebuildStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
