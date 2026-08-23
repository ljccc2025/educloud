package com.educloud.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容就绪投影（course_content_readiness_projection）：一课程一行 + source_event_id 唯一（幂等）；M05 仅建表不激活 gate。
 * 表名与 V001__course.sql 对齐；主键为雪花 ID（ASSIGN_ID，DB 无自增）。
 */
@Data
@TableName("course_content_readiness_projection")
public class CourseContentReadinessProjectionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 一课程一行（uk_course_readiness_course）。 */
    private Long courseId;

    private Long contentRootId;

    private Long publishedRevisionId;

    /** TINYINT(1)：内容就绪标记。 */
    private Boolean ready;

    /** 幂等键（uk_course_readiness_event）。 */
    private String sourceEventId;

    /** 最近一次内容聚合版本（普通字段，非乐观锁）。 */
    private Long lastAggregateVersion;

    private LocalDateTime updatedAt;
}
