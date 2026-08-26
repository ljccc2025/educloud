package com.educloud.search.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 搜索多维聚合统计响应模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchAggregations implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分类聚合桶列表 */
    @Builder.Default
    private List<FacetItem> categories = new ArrayList<>();

    /** 难度等级聚合桶列表 */
    @Builder.Default
    private List<FacetItem> difficulties = new ArrayList<>();

    /** 价格区间聚合桶列表 */
    @Builder.Default
    private List<FacetItem> priceRanges = new ArrayList<>();

    /**
     * 空聚合对象工厂方法
     */
    public static SearchAggregations empty() {
        return SearchAggregations.builder()
                .categories(new ArrayList<>())
                .difficulties(new ArrayList<>())
                .priceRanges(new ArrayList<>())
                .build();
    }

    /**
     * 单个聚合桶项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FacetItem implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 聚合键（如分类名称、难度枚举、区间标识） */
        private String key;

        /** 匹配文档数量 */
        private Long count;
    }
}
