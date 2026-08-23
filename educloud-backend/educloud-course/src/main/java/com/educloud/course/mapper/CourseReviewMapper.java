package com.educloud.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.course.entity.CourseReviewEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 评价数据访问（CourseReviewEntity，M05 任务 14）。
 *
 * <p>upsert：INSERT ... ON DUPLICATE KEY UPDATE（uk(course_id, student_id)），
 * rating/content/status/updated_by/updated_at 全量覆盖；status 由调用方固定传
 * VISIBLE（学生重复提交即恢复可见）。id 由服务层显式 IdWorker 生成（自定义
 * @Insert 不经 BaseMapper.insert，ASSIGN_ID 不生效），更新分支不改 id。
 * selectVisibleSummary：仅 VISIBLE 评价聚合（HIDDEN 不计入评分汇总）。</p>
 */
@Mapper
public interface CourseReviewMapper extends BaseMapper<CourseReviewEntity> {

    /**
     * 评价 upsert（MySQL 8.0.19+ VALUES 别名语法；8.0.36 兼容，替代已废弃的
     * VALUES() 函数形式）。主键/uk 冲突走更新分支。
     */
    @Insert("""
            INSERT INTO course_review
              (id, course_id, student_id, rating, content, status,
               created_by, created_at, updated_by, updated_at)
            VALUES
              (#{id}, #{courseId}, #{studentId}, #{rating}, #{content}, #{status},
               #{createdBy}, #{createdAt}, #{updatedBy}, #{updatedAt})
            AS new
            ON DUPLICATE KEY UPDATE
              rating = new.rating,
              content = new.content,
              status = new.status,
              updated_by = new.updated_by,
              updated_at = new.updated_at
            """)
    int upsert(CourseReviewEntity review);

    /**
     * VISIBLE 评价评分汇总（同事务重算 course.rating_avg/rating_count 的 SQL 聚合；
     * 无 VISIBLE 行时 AVG 为 NULL，由服务层归一化为 0.00/0）。
     */
    @Select("SELECT AVG(rating) AS rating_avg, COUNT(*) AS rating_count "
            + "FROM course_review WHERE course_id = #{courseId} AND status = 'VISIBLE'")
    CourseReviewSummaryRow selectVisibleSummary(@Param("courseId") Long courseId);
}
