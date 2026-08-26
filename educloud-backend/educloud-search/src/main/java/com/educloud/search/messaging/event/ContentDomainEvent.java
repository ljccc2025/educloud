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
 * 章节/课件内容领域事件载荷模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContentDomainEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息/事件全局唯一 ID */
    @JsonAlias({"eventId", "id"})
    private String messageId;

    /** 事件类型：LessonPublished, LessonUpdated, LessonDeleted, ContentRevisionPublished 等 */
    private String eventType;

    /** 聚合根类型：CourseContent / Lesson / Chapter */
    private String aggregateType;

    /** 聚合根 ID */
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

    /** 兼容 occurredAt */
    private String occurredAt;

    /** 课件内容业务载荷详情 */
    private ContentEventData data;

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
     * 课件内容事件业务数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentEventData implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 所属课程 ID */
        private Long courseId;

        /** 内容根 ID */
        private Long contentRootId;

        /** 课件 ID */
        private Long lessonId;

        /** 课件标题 */
        @JsonAlias({"title", "lessonTitle"})
        private String title;

        /** 章节 ID */
        private Long chapterId;

        /** 章节标题 */
        private String chapterTitle;

        /** 是否支持免费试看 */
        private Boolean isPreview;

        /** 课件排序序号 */
        private Integer sortOrder;

        /** 操作类型：ADD / UPDATE / DELETE */
        private String action;

        /** 发布版本修订号 ID */
        private Long publishedRevisionId;

        /** 修订号版本 */
        private Integer revisionNo;

        /** 修订版本包含的完整课件列表 */
        private List<LessonDoc> lessons;

        /** 更新时间 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updatedAt;

        /** 发布时间 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime publishedAt;
    }
}
