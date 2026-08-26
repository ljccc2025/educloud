package com.educloud.search.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 课程全文检索与筛选查询请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSearchQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 搜索关键词（支持多字段加权与分词匹配） */
    private String keyword;

    /** 课程分类筛选（支持分类名称或编码，如 '后端开发' 或 'BACKEND'） */
    private String category;

    /** 难度等级筛选：BEGINNER / INTERMEDIATE / ADVANCED */
    private String difficulty;

    /** 是否仅看免费课程 */
    private Boolean isFree;

    /** 价格下限（分） */
    @Min(value = 0, message = "最小价格不能小于 0")
    private Long minPriceCents;

    /** 价格上限（分） */
    @Min(value = 0, message = "最大价格不能小于 0")
    private Long maxPriceCents;

    /**
     * 排序策略：
     * - relevance: 综合相关度 (_score DESC, publishedAt DESC) [默认]
     * - popular: 热门程度 (studentCount DESC, rating DESC)
     * - newest: 最新发布 (publishedAt DESC)
     * - price_asc: 价格从低到高 (priceCents ASC)
     * - price_desc: 价格从高到低 (priceCents DESC)
     */
    @Builder.Default
    private String sortBy = "relevance";

    /** 当前页码（从 1 开始） */
    @Min(value = 1, message = "页码最小为 1")
    @Builder.Default
    private Integer page = 1;

    /** 每页条数（默认 20，最大限制 50） */
    @Min(value = 1, message = "每页数量最小为 1")
    @Max(value = 50, message = "每页数量最大不能超过 50")
    @Builder.Default
    private Integer size = 20;

    public Integer getPage() {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    public Integer getSize() {
        if (size == null || size < 1) {
            return 20;
        }
        if (size > 50) {
            return 50;
        }
        return size;
    }

    public String getSortBy() {
        if (sortBy == null || sortBy.isBlank()) {
            return "relevance";
        }
        return sortBy;
    }
}
