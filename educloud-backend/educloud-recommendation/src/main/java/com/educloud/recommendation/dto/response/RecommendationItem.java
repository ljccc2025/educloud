package com.educloud.recommendation.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecommendationItem {
    private String courseId;      // Snowflake 字符串
    private String title;
    private String categoryId;
    private String categoryName;
    private String coverUrl;      // MinIO 公开直链
    private String price;         // 十进制金额字符串（元），与 CourseSummaryResponse 对齐
    private String reason;
    private String strategy;      // POPULAR / NEW / SIMILAR
}
