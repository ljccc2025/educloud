package com.educloud.search.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 智能补全与建议单项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 建议文本（如 "Spring Cloud 微服务实战"） */
    private String text;

    /** 高亮建议文本（如 "<em>Spring</em> Cloud 微服务实战"） */
    private String highlight;

    /** 所属分类 */
    private String category;

    /** 建议类型：COURSE（直接课程目标） / KEYWORD（热搜关键词） */
    private String type;

    /** 关联目标 ID（若 type 为 COURSE 则为课程 ID） */
    private String targetId;

    /** 匹配权重/分值 */
    private Float score;
}
