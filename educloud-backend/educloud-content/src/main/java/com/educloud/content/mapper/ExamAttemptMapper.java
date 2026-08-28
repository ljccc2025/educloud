package com.educloud.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.educloud.content.entity.ExamAttemptEntity;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ExamAttemptMapper extends BaseMapper<ExamAttemptEntity> {

    /** CAS 判分收尾：仅 IN_PROGRESS 可迁移到 GRADED，返回 0 表示已被并发提交/不存在。 */
    @Update("UPDATE exam_attempt SET status='GRADED', score=#{score}, passed=#{passed}, "
            + "answers_json=#{answersJson}, submitted_at=#{submittedAt}, "
            + "tab_switch_count=#{tabSwitchCount}, flagged=#{flagged}, timeout=#{timeout} "
            + "WHERE id=#{id} AND status='IN_PROGRESS'")
    int markGraded(ExamAttemptEntity attempt);

    /** 进行中的考试记录（超时判定在应用侧，避免 DB 容器 UTC 时钟与 started_at 本地时间直接比较）。 */
    @Select("SELECT a.* FROM exam_attempt a WHERE a.status = 'IN_PROGRESS' LIMIT 100")
    List<ExamAttemptEntity> selectExpiredInProgress();
}
