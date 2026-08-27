package com.educloud.recommendation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.recommendation.entity.RecommendationFeedbackEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RecommendationFeedbackMapper extends BaseMapper<RecommendationFeedbackEntity> {

    @Insert("""
            INSERT INTO recommendation_feedback (user_id, course_id, action, reason)
            VALUES (#{userId}, #{courseId}, #{action}, #{reason})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertOrIgnore(@Param("userId") Long userId,
                       @Param("courseId") Long courseId,
                       @Param("action") String action,
                       @Param("reason") String reason);
}
