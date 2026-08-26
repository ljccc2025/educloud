package com.educloud.search.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum TaskType {
    @EnumValue
    FULL_REBUILD,

    @EnumValue
    INCREMENTAL_REPAIR
}
