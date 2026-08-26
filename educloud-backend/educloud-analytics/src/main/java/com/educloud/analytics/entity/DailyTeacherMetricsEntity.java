package com.educloud.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("daily_teacher_metrics")
public class DailyTeacherMetricsEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("teacher_id")
    private String teacherId;

    @TableField("metric_date")
    private LocalDate metricDate;

    @TableField("new_enrollments")
    private Integer newEnrollments;

    @TableField("revenue_cents")
    private Long revenueCents;

    @TableField("active_students")
    private Integer activeStudents;

    @TableField("completed_courses_count")
    private Integer completedCoursesCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
