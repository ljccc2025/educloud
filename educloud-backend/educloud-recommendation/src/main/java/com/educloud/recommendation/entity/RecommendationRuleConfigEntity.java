package com.educloud.recommendation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("recommendation_rule_config")
public class RecommendationRuleConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("rule_key")
    private String ruleKey;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("weight")
    private Integer weight;

    @TableField("config_version")
    private Integer configVersion;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
