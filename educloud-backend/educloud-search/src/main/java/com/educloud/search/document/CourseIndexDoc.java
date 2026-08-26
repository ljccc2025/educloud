package com.educloud.search.document;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch 课程索引 Document 模型
 * 对应索引 Schema：elasticsearch/course-v1.json
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseIndexDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ES 文档唯一主键 (课程雪花 ID 字符串) */
    private String id;

    /** 课程聚合根 ID (String) */
    private String courseId;

    /** 课程标题 (支持全文检索与 completion 自动补全) */
    private String title;

    /** 课程副标题 */
    private String subtitle;

    /** 课程详细介绍 */
    private String description;

    /** 授课讲师 ID */
    private String teacherId;

    /** 讲师姓名 */
    private String teacherName;

    /** 分类名称（如：后端开发、前端开发） */
    private String category;

    /** 分类编码（如：BACKEND, FRONTEND） */
    private String categoryCode;

    /** 课程封面 URL */
    private String coverUrl;

    /** 难度等级：BEGINNER / INTERMEDIATE / ADVANCED */
    private String difficulty;

    /** 课程价格（分） */
    private Long priceCents;

    /** 是否免费课程 */
    private Boolean isFree;

    /** 综合评分 (0.0 ~ 5.0) */
    private Float rating;

    /** 学习学员总数 */
    private Integer studentCount;

    /** 课件总数 */
    private Integer lessonCount;

    /** 课程生命周期状态：PUBLISHED / OFFLINE / etc. */
    private String status;

    /** 标签列表 (Keyword) */
    private List<String> tags;

    /** 嵌套课件列表 (Nested) */
    private List<LessonDoc> lessons;

    /** 领域事件单调递增版本号 (用于并发防乱序) */
    private Long aggregateVersion;

    /** 发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishedAt;

    /** 最后更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
