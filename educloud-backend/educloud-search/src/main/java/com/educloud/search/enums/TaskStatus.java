package com.educloud.search.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum TaskStatus {
    @EnumValue
    PENDING,

    @EnumValue
    RUNNING,

    @EnumValue
    SUCCESS,

    @EnumValue
    FAILED
}
