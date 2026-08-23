package com.educloud.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.course.entity.CourseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 课程聚合根数据访问（CourseEntity）。 */
@Mapper
public interface CourseMapper extends BaseMapper<CourseEntity> {

    /**
     * 选课计数递增（M05 任务 13）：enrollment_count+1 且携带锁根读到的 version 条件
     * （乐观锁兜底；调用方已在锁根事务内，version 不会漂移，0 行命中 → VERSION_CONFLICT）。
     */
    @Update("UPDATE course SET enrollment_count = enrollment_count + 1 "
            + "WHERE id = #{id} AND version = #{version}")
    int incrementEnrollmentCount(@Param("id") Long id, @Param("version") Long version);

    /**
     * 评分汇总列更新（M05 任务 14）：评价 upsert/隐藏后同事务按 VISIBLE 评价聚合
     * 重算 rating_avg/rating_count（HIDDEN 不计入）。聚合列直写，与
     * incrementEnrollmentCount 同一风格（不动乐观锁 version；调用方已持课程根行锁）。
     */
    @Update("UPDATE course SET rating_avg = #{ratingAvg}, rating_count = #{ratingCount} "
            + "WHERE id = #{id}")
    int updateRatingSummary(
            @Param("id") Long id,
            @Param("ratingAvg") java.math.BigDecimal ratingAvg,
            @Param("ratingCount") Integer ratingCount);

    /**
     * 锁定课程根行（SELECT ... FOR UPDATE）：审批/驳回/撤回等根状态切换前加行锁，
     * 与乐观锁（@Version）配合保证并发提交/审批原子性（规格 §7 版本乐观锁）。
     */
    @Select("SELECT * FROM course WHERE id = #{id} FOR UPDATE")
    CourseEntity selectByIdForUpdate(@Param("id") Long id);

    /**
     * 公开列表分页查询（GET /api/v1/courses，M05 任务 11）：course JOIN 已发布版本
     * JOIN 分类 JOIN 负责人授课教师（展示名以 teacher_id 占位），仅 lifecycle_status=
     * PUBLISHED；SQL 分页由 PaginationInnerInterceptor 处理（jsqlparser 已配）。
     *
     * <p>过滤/排序契约（规格 §6，服务层已校验白名单后原样转发）：
     * 分类仅 JOIN status=VISIBLE（隐藏分类下的已发布课程不出现，与任务 7 分类可见性对齐）；
     * keyword 匹配 title/subtitle/description（COALESCE 防空）；priceRange 语义
     * free=price 0、under200=(0,200)、200to400=[200,400]、above400=&gt;400；sort 白名单
     * popular→enrollment_count DESC、newest→published_at DESC、price-asc/price-desc→
     * price、rating→rating_avg DESC，缺省 popular（ORDER BY 均附 id 稳定次序）。
     * enrollment_count/rating_avg/rating_count 取 course 聚合列。</p>
     */
    @Select("""
            <script>
            SELECT c.id AS course_id,
                   c.published_version_id AS published_version_id,
                   c.published_at AS published_at,
                   c.rating_avg AS rating_avg,
                   c.rating_count AS rating_count,
                   c.enrollment_count AS enrollment_count,
                   v.title AS title,
                   v.cover_file_id AS cover_file_id,
                   v.level AS level,
                   v.price AS price,
                   v.currency AS currency,
                   v.category_id AS category_id,
                   cat.name AS category_name,
                   t.teacher_id AS teacher_id
            FROM course c
            JOIN course_version v ON v.id = c.published_version_id
            JOIN course_category cat ON cat.id = v.category_id AND cat.status = 'VISIBLE'
            JOIN course_teacher t ON t.course_id = c.id AND t.teacher_role = 'OWNER'
            WHERE c.lifecycle_status = 'PUBLISHED'
            <if test="keyword != null and keyword != ''">
              AND (v.title LIKE CONCAT('%', #{keyword}, '%')
                   OR COALESCE(v.subtitle, '') LIKE CONCAT('%', #{keyword}, '%')
                   OR COALESCE(v.description, '') LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="categoryId != null">
              AND v.category_id = #{categoryId}
            </if>
            <if test="level != null and level != ''">
              AND v.level = #{level}
            </if>
            <choose>
              <when test="priceRange == 'free'">AND v.price = 0</when>
              <when test="priceRange == 'under200'">AND v.price &gt; 0 AND v.price &lt; 200</when>
              <when test="priceRange == '200to400'">AND v.price &gt;= 200 AND v.price &lt;= 400</when>
              <when test="priceRange == 'above400'">AND v.price &gt; 400</when>
            </choose>
            ORDER BY
            <choose>
              <when test="sort == 'newest'">c.published_at DESC, c.id DESC</when>
              <when test="sort == 'price-asc'">v.price ASC, c.id ASC</when>
              <when test="sort == 'price-desc'">v.price DESC, c.id DESC</when>
              <when test="sort == 'rating'">c.rating_avg DESC, c.rating_count DESC, c.id DESC</when>
              <otherwise>c.enrollment_count DESC, c.id DESC</otherwise>
            </choose>
            </script>
            """)
    IPage<CourseCatalogRow> selectCatalogPage(
            Page<CourseCatalogRow> page,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("level") String level,
            @Param("priceRange") String priceRange,
            @Param("sort") String sort);

    /**
     * 教师课程管理列表分页（GET /api/v1/teacher/courses，M05 任务 22）：course_teacher
     * JOIN course JOIN course_version（COALESCE(draft_version_id, published_version_id)，
     * 草稿优先——有草稿显示草稿状态，无草稿回落发布版本）一次取回列表项；仅当前教师
     * 归属（OWNER/CO_TEACHER）的课程。按 updated_at 倒序（附 id 稳定次序）。
     * SQL 分页由 PaginationInnerInterceptor 处理。
     */
    @Select("""
            <script>
            SELECT c.id AS course_id,
                   COALESCE(c.draft_version_id, c.published_version_id) AS version_id,
                   c.lifecycle_status AS lifecycle_status,
                   c.enrollment_count AS enrollment_count,
                   v.title AS title,
                   v.cover_file_id AS cover_file_id,
                   v.level AS level,
                   v.price AS price,
                   v.currency AS currency,
                   v.category_id AS category_id,
                   v.version_status AS version_status
            FROM course_teacher t
            JOIN course c ON c.id = t.course_id
            JOIN course_version v ON v.id = COALESCE(c.draft_version_id, c.published_version_id)
            WHERE t.teacher_id = #{teacherId}
            ORDER BY c.updated_at DESC, c.id DESC
            </script>
            """)
    IPage<CourseTeacherRow> selectTeacherCoursesPage(
            Page<CourseTeacherRow> page,
            @Param("teacherId") Long teacherId);
}
