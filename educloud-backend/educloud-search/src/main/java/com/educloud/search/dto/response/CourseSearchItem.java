package com.educloud.search.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程检索卡片数据传输对象（支持雪花 ID 字符串无损）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSearchItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 课程主键 ID (String 避免前端 JS 精度丢失) */
    private String id;

    /** 课程聚合根 ID (String) */
    private String courseId;

    /** 课程标题（命中关键词时包含 <em class="search-highlight"> 高亮标签） */
    private String title;

    /** 课程副标题（命中时带高亮） */
    private String subtitle;

    /** 课程详情摘要（命中时带高亮） */
    private String description;

    /** 讲师 ID (String) */
    private String teacherId;

    /** 讲师姓名 */
    private String teacherName;

    /** 课程分类名称 */
    private String category;

    /** 课程分类编码 */
    private String categoryCode;

    /** 课程封面图片 URL */
    private String coverUrl;

    /** 难度等级：BEGINNER / INTERMEDIATE / ADVANCED */
    private String difficulty;

    /** 课程价格（分） */
    private Long priceCents;

    /** 是否免费课程 */
    private Boolean isFree;

    /** 课程综合评分 (0.0 ~ 5.0) */
    private Float rating;

    /** 学习学员总数 */
    private Integer studentCount;

    /** 课件总数 */
    private Integer lessonCount;

    /** 课程生命周期状态：PUBLISHED */
    private String status;

    /** 课程标签列表 */
    private List<String> tags;

    /** 检索相关度打分 */
    private Double score;

    /** 发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishedAt;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
