package com.educloud.recommendation.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.recommendation.entity.RecommendationFeedbackEntity;
import com.educloud.recommendation.mapper.RecommendationFeedbackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final RecommendationFeedbackMapper feedbackMapper;

    /**
     * 记录「不感兴趣」；重复反馈幂等（唯一约束 + ON DUPLICATE KEY 静默成功）；
     * DB 故障时静默成功（规格 §8：反馈写入失败不阻塞 UI）。
     */
    public void dislike(Long userId, Long courseId, String reason) {
        try {
            feedbackMapper.insertOrIgnore(userId, courseId, "DISLIKE", reason);
        } catch (Exception e) {
            log.warn("Feedback insert failed, ignored: {}", e.getMessage());
        }
    }

    /** 用户已 DISLIKE 的课程 ID 集合；异常时返回空集（不影响推荐主流程） */
    public Set<Long> dislikedCourseIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        try {
            List<Object> ids = feedbackMapper.selectObjs(
                    new QueryWrapper<RecommendationFeedbackEntity>()
                            .select("course_id")
                            .eq("user_id", userId)
                            .eq("action", "DISLIKE")
                            .last("LIMIT 500"));
            Set<Long> set = new HashSet<>();
            for (Object id : ids) {
                set.add(((Number) id).longValue());
            }
            return set;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
}
