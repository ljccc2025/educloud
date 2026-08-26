package com.educloud.analytics.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum RebuildStage {
    INITIALIZING("INITIALIZING", "初始化准备"),
    USER("USER", "抽取用户与活跃数据"),
    COURSE("COURSE", "抽取课程与选课数据"),
    PAYMENT("PAYMENT", "抽取流水与退款数据"),
    COMPLETED("COMPLETED", "全量重算完成");

    @EnumValue
    @JsonValue
    private final String code;
    private final String description;

    RebuildStage(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
