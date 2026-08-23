package com.educloud.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程不可变版本（course_version）：提交审核后不可原地修改；(course_id, version_no) 唯一。
 * 表名与 V001__course.sql 对齐；主键为雪花 ID（ASSIGN_ID，DB 无自增）。
 */
@Data
@TableName("course_version")
public class CourseVersionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long courseId;

    /** 版本号；(course_id, version_no) 唯一（uk_course_version_no）。 */
    private Integer versionNo;

    private Long categoryId;

    private String title;

    private String subtitle;

    private String description;

    private Long coverFileId;

    /** BEGINNER/INTERMEDIATE/ADVANCED。 */
    private String level;

    private BigDecimal price;

    private String currency;

    /** DRAFT/PENDING_REVIEW/REJECTED/PUBLISHED/SUPERSEDED/WITHDRAWN。 */
    private String versionStatus;

    /** 内容摘要（提交审核后不可原地修改，内容变更需新版本）。 */
    private String contentHash;

    private Long createdBy;

    private LocalDateTime createdAt;
}
