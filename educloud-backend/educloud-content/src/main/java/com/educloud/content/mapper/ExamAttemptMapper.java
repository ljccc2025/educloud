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

    /** 超时未交卷的进行中记录（服务端时间，JOIN exam 取时长）。 */
    @Select("SELECT a.* FROM exam_attempt a JOIN exam e ON a.exam_id = e.id "
            + "WHERE a.status = 'IN_PROGRESS' "
            + "AND TIMESTAMPADD(MINUTE, e.duration_minutes, a.started_at) <= NOW(3) "
            + "LIMIT 100")
    List<ExamAttemptEntity> selectExpiredInProgress();
}
