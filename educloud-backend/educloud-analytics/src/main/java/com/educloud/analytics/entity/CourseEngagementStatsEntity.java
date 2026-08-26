package com.educloud.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("course_engagement_stats")
public class CourseEngagementStatsEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("course_id")
    private String courseId;

    @TableField("course_title")
    private String courseTitle;

    @TableField("teacher_id")
    private String teacherId;

    @TableField("total_enrollments")
    private Integer totalEnrollments;

    @TableField("active_learners")
    private Integer activeLearners;

    @TableField("completed_count")
    private Integer completedCount;

    @TableField("completion_rate")
    private BigDecimal completionRate;

    @TableField("avg_rating")
    private BigDecimal avgRating;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
