package com.educloud.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.analytics.entity.CourseEngagementStatsEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CourseEngagementStatsMapper extends BaseMapper<CourseEngagementStatsEntity> {

    int upsertCourseStats(
            @Param("courseId") String courseId,
            @Param("courseTitle") String courseTitle,
            @Param("teacherId") String teacherId,
            @Param("enrollmentDelta") int enrollmentDelta,
            @Param("completedDelta") int completedDelta
    );

    @Select("""
        SELECT *
        FROM course_engagement_stats
        WHERE teacher_id = #{teacherId}
        ORDER BY total_enrollments DESC
        LIMIT #{limit}
    """)
    List<CourseEngagementStatsEntity> selectTopRankedCourses(
            @Param("teacherId") String teacherId,
            @Param("limit") int limit
    );
}
