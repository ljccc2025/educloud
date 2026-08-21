package com.educloud.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** Outbox 水位（outbox_sequence）：业务事务锁定并递增，保证 source_sequence 单调。 */
@Data
@TableName("outbox_sequence")
public class OutboxSequenceEntity {

    @TableId
    private String sourceName;

    private Long lastValue;
}
