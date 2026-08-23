package com.educloud.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价（course_review）：rating 1-5；(course_id, student_id) 唯一（upsert 语义）；评价表是事实来源。
 * 表名与 V001__course.sql 对齐；主键为雪花 ID（ASSIGN_ID，DB 无自增）。
 */
@Data
@TableName("course_review")
public class CourseReviewEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long courseId;

    private Long studentId;

    /** 1-5。 */
    private Integer rating;

    private String content;

    /** VISIBLE/HIDDEN。 */
    private String status;

    private Long createdBy;

    private LocalDateTime createdAt;

    private Long updatedBy;

    private LocalDateTime updatedAt;
}
