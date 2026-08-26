package com.educloud.search.messaging.event;

import com.educloud.search.document.LessonDoc;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程领域事件载荷模型
 * 兼容 EventEnvelope 格式与自定义事件载荷
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CourseDomainEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息/事件全局唯一 ID */
    @JsonAlias({"eventId", "id"})
    private String messageId;

    /** 事件类型：CoursePublished, CourseUpdated, CourseOfflined, CourseOffline, CourseDeleted, CourseRepublished, CourseArchived 等 */
    private String eventType;

    /** 聚合根类型：Course */
    private String aggregateType;

    /** 聚合根 ID (课程 ID) */
    private String aggregateId;

    /** 聚合根单调递增版本号 (用于并发防乱序) */
    private Long aggregateVersion;

    /** 事件产生源服务 */
    private String sourceService;

    /** 事件产生序列号 */
    private Long sourceSequence;

    /** 请求追踪 ID */
    private String requestId;

    /** 全局分布式追踪 ID */
    private String traceId;

    /** 事件发生时间戳 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    /** 兼容 EventEnvelope 中的 occurredAt */
    private String occurredAt;

    /** 事件业务数据详情 */
    private CourseEventData data;

    /**
     * 获取有效的 messageId
     */
    public String getEffectiveMessageId() {
        if (messageId != null && !messageId.isBlank()) {
            return messageId.trim();
        }
        return aggregateId != null ? aggregateId + "_" + (aggregateVersion != null ? aggregateVersion : "0") : "UNKNOWN";
    }

    /**
     * 课程事件业务载荷详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CourseEventData implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 课程 ID */
        private Long courseId;

        /** 版本 ID */
        private Long versionId;

        /** 课程标题 */
        private String title;

        /** 课程副标题 */
        private String subtitle;

        /** 课程描述 */
        private String description;

        /** 授课讲师 ID */
        private String teacherId;

        /** 讲师姓名 */
        private String teacherName;

        /** 课程分类名称 */
        private String category;

        /** 课程分类编码 */
        private String categoryCode;

        /** 课程封面图 URL */
        private String coverUrl;

        /** 难度级别：BEGINNER / INTERMEDIATE / ADVANCED */
        private String difficulty;

        /** 课程价格（分） */
        private Long priceCents;

        /** 是否免费课程 */
        private Boolean isFree;

        /** 综合评分 */
        private Float rating;

        /** 报名学员数 */
        private Integer studentCount;

        /** 课件总数 */
        private Integer lessonCount;

        /** 生命周期状态：PUBLISHED / OFFLINE / ARCHIVED / DELETED 等 */
        @JsonAlias({"status", "lifecycleStatus"})
        private String lifecycleStatus;

        /** 标签列表 */
        private List<String> tags;

        /** 嵌套课件列表 */
        private List<LessonDoc> lessons;

        /** 发布时间 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime publishedAt;

        /** 更新时间 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updatedAt;

        /** 下架时间 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime offlinedAt;

        /** 归档时间 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime archivedAt;
    }
}
