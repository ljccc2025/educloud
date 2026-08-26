package com.educloud.search.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 课程检索统一响应模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSearchResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 命中结果总条数 */
    @Builder.Default
    private Long total = 0L;

    /** 当前页码 */
    @Builder.Default
    private Integer page = 1;

    /** 每页数量 */
    @Builder.Default
    private Integer size = 20;

    /** 是否处于数据库兜底降级模式 */
    @JsonProperty("isDegraded")
    @Builder.Default
    private Boolean isDegraded = false;

    /** 课程搜索列表项 */
    @Builder.Default
    private List<CourseSearchItem> items = new ArrayList<>();

    /** 多维聚合筛选数据（降级模式下为 empty） */
    @Builder.Default
    private SearchAggregations aggregations = SearchAggregations.empty();

    /**
     * 构建空响应
     */
    public static CourseSearchResponse empty(int page, int size) {
        return CourseSearchResponse.builder()
                .total(0L)
                .page(page)
                .size(size)
                .isDegraded(false)
                .items(new ArrayList<>())
                .aggregations(SearchAggregations.empty())
                .build();
    }

    /**
     * 构建降级响应
     */
    public static CourseSearchResponse degraded(long total, int page, int size, List<CourseSearchItem> items) {
        return CourseSearchResponse.builder()
                .total(total)
                .page(page)
                .size(size)
                .isDegraded(true)
                .items(items != null ? items : new ArrayList<>())
                .aggregations(SearchAggregations.empty())
                .build();
    }
}
