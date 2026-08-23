package com.educloud.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.course.entity.CourseReviewEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

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

    /**
     * 管理端隐藏窄更新（P1b 竞态修复）：只写 status/updated_by/updated_at，WHERE id=?，
     * 不触碰 rating/content —— 与「学生并发改分」天然免疫（陈旧实体整行 updateById
     * 会把旧评分/内容回写覆盖学生新提交）。调用方已持课程根行锁。
     */
    @Update("UPDATE course_review SET status = #{status}, updated_by = #{updatedBy}, "
            + "updated_at = #{updatedAt} WHERE id = #{id}")
    int updateStatus(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("updatedBy") Long updatedBy,
            @Param("updatedAt") LocalDateTime updatedAt);
}
