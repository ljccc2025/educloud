package com.educloud.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程聚合根（course）：公开读取只跟随 published_version_id，教师编辑只操作 draft_version_id；version 为乐观锁。
 * 表名与 V001__course.sql 对齐；主键为雪花 ID（ASSIGN_ID，DB 无自增）。
 */
@Data
@TableName("course")
public class CourseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 负责人教师 ID。 */
    private Long ownerTeacherId;

    /** DRAFT/PENDING_REVIEW/PUBLISHED/OFFLINE/ARCHIVED。 */
    private String lifecycleStatus;

    /** 公开读取跟随的已发布版本。 */
    private Long publishedVersionId;

    /** 教师编辑中的草稿版本。 */
    private Long draftVersionId;

    private LocalDateTime publishedAt;

    private BigDecimal ratingAvg;

    private Integer ratingCount;

    private Integer enrollmentCount;

    /** 乐观锁版本：由 MyBatis-Plus 拦截器在 update 时自动递增并回写，禁止手动 +1。 */
    @Version
    private Long version;

    private Long createdBy;

    private LocalDateTime createdAt;

    private Long updatedBy;

    private LocalDateTime updatedAt;
}
