package com.educloud.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.analytics.entity.DailyTeacherMetricsEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface DailyTeacherMetricsMapper extends BaseMapper<DailyTeacherMetricsEntity> {

    int upsertIncrement(
            @Param("teacherId") String teacherId,
            @Param("metricDate") LocalDate metricDate,
            @Param("newEnrollments") int newEnrollments,
            @Param("revenueCents") long revenueCents,
            @Param("activeStudents") int activeStudents,
            @Param("completedCourses") int completedCourses
    );

    @Select("""
        SELECT 
            DATE_FORMAT(metric_date, '%Y-%m') AS month,
            COALESCE(SUM(new_enrollments), 0) AS total_enrollments
        FROM daily_teacher_metrics
        WHERE teacher_id = #{teacherId}
          AND metric_date >= #{startDate}
          AND metric_date <= #{endDate}
        GROUP BY DATE_FORMAT(metric_date, '%Y-%m')
        ORDER BY month ASC
    """)
    List<Map<String, Object>> selectMonthlyEnrollmentTrend(
            @Param("teacherId") String teacherId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Select("""
        SELECT 
            DATE_FORMAT(metric_date, '%Y-%m') AS month,
            COALESCE(SUM(revenue_cents), 0) AS total_revenue_cents
        FROM daily_teacher_metrics
        WHERE teacher_id = #{teacherId}
          AND metric_date >= #{startDate}
          AND metric_date <= #{endDate}
        GROUP BY DATE_FORMAT(metric_date, '%Y-%m')
        ORDER BY month ASC
    """)
    List<Map<String, Object>> selectMonthlyRevenueTrend(
            @Param("teacherId") String teacherId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Select("""
        SELECT 
            COALESCE(SUM(new_enrollments), 0) AS total_students,
            COALESCE(SUM(revenue_cents), 0) AS total_revenue_cents,
            COALESCE(SUM(active_students), 0) AS total_active_students,
            COALESCE(SUM(completed_courses_count), 0) AS total_completed_courses
        FROM daily_teacher_metrics
        WHERE teacher_id = #{teacherId}
    """)
    Map<String, Object> selectTeacherSummary(@Param("teacherId") String teacherId);
}
