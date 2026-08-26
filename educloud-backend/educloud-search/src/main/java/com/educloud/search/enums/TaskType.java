package com.educloud.search.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TaskType {
    FULL_REBUILD("FULL_REBUILD"),
    INCREMENTAL_REPAIR("INCREMENTAL_REPAIR");

    @EnumValue
    @JsonValue
    private final String value;
}
