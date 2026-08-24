package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("outbox_sequence")
public class OutboxSequenceEntity {
    @TableId
    private String sourceName;

    private Long lastValue;
}
