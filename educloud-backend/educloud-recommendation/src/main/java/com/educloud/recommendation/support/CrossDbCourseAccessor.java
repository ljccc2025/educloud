package com.educloud.recommendation.support;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 跨库课程只读访问器（与 analytics 模块 CrossDbBatchExtractor 同模式）：
 * 推荐服务通过主数据源 JdbcTemplate 直接跨库只读查询 educloud_course / educloud_file。
 * 每个查询独立容错：失败返回空集合/空 map，绝不将异常传播到推荐引擎。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossDbCourseAccessor {

    private final JdbcTemplate jdbcTemplate;

    /** 单条 IN 子句最大占位符数，防止 SQL 占位符溢出（MySQL 上限 65535） */
    private static final int MAX_IN_IDS = 1000;

    /** 课程只读行：educloud_course 各表 JOIN 后的扁平结构 */
    @Data
    public static class CourseRow {
        private Long courseId;
        private String title;
        private Long categoryId;
        private String categoryName;
        private LocalDateTime publishedAt;
        private BigDecimal price;
        private Long coverFileId;
        private Integer enrollmentCount;
        private BigDecimal ratingAvg;
    }

    /**
     * 可见课程列表：跟随 published_version_id 取最新发布版本，JOIN 分类名称，按发布时间倒序。
     */
    public List<CourseRow> findVisibleCourses() {
        try {
            String sql = "SELECT c.id AS course_id, v.title, v.category_id,\n"
                    + "       cat.name AS category_name, c.published_at,\n"
                    + "       v.price, v.cover_file_id,\n"
                    + "       c.enrollment_count, c.rating_avg\n"
                    + "FROM educloud_course.course c\n"
                    + "JOIN educloud_course.course_version v ON v.id = c.published_version_id\n"
                    + "JOIN educloud_course.course_category cat ON cat.id = v.category_id AND cat.status = 'VISIBLE'\n"
                    + "WHERE c.lifecycle_status = 'PUBLISHED' AND c.published_version_id IS NOT NULL\n"
                    + "ORDER BY c.published_at DESC";
            return jdbcTemplate.query(sql, this::mapCourseRow);
        } catch (Exception e) {
            log.warn("Cross-db query educloud_course visible courses failed, fallback to empty: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 学生 ACTIVE 选课课程 id 列表（推荐过滤已购/已选课程用）。
     */
    public List<Long> findEnrolledCourseIds(Long studentId) {
        try {
            String sql = "SELECT course_id FROM educloud_course.course_enrollment"
                    + " WHERE student_id = ? AND status = 'ACTIVE'";
            return jdbcTemplate.queryForList(sql, Long.class, studentId);
        } catch (Exception e) {
            log.warn("Cross-db query educloud_course.course_enrollment failed, fallback to empty: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 学生选课上下文（课程名 + 分类，最多 50 条），复用 mapCourseRow 映射。
     */
    public List<CourseRow> findEnrolledCourseContexts(Long studentId) {
        try {
            String sql = "SELECT c.id AS course_id, v.title, v.category_id,\n"
                    + "       cat.name AS category_name, c.published_at,\n"
                    + "       v.price, v.cover_file_id,\n"
                    + "       c.enrollment_count, c.rating_avg\n"
                    + "FROM educloud_course.course_enrollment e\n"
                    + "JOIN educloud_course.course c ON c.id = e.course_id\n"
                    + "JOIN educloud_course.course_version v ON v.id = c.published_version_id\n"
                    + "JOIN educloud_course.course_category cat ON cat.id = v.category_id AND cat.status = 'VISIBLE'\n"
                    + "WHERE e.student_id = ? AND e.status = 'ACTIVE'\n"
                    + "ORDER BY e.enrolled_at DESC\n"
                    + "LIMIT 50";
            return jdbcTemplate.query(sql, this::mapCourseRow, studentId);
        } catch (Exception e) {
            log.warn("Cross-db query enrolled course contexts failed, fallback to empty: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 封面文件 id -> 可访问 URL（MinIO 直链 http://192.168.100.136:9000/{bucket}/{object_key}）。
     * 空集合直接返回 emptyMap，不触发查询。
     */
    public Map<Long, String> findCoverUrls(Set<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> urls = new HashMap<>();
        // 按 MAX_IN_IDS 分批查询，防止单条 IN 子句占位符溢出；某批失败仅丢该批，不影响其他批次
        List<Long> ids = new ArrayList<>(fileIds);
        for (int start = 0; start < ids.size(); start += MAX_IN_IDS) {
            List<Long> batch = ids.subList(start, Math.min(start + MAX_IN_IDS, ids.size()));
            try {
                String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
                String sql = "SELECT id, bucket, object_key FROM educloud_file.file_object"
                        + " WHERE id IN (" + placeholders + ") AND status = 'AVAILABLE'";
                // 行映射器仅用于收集 url，返回值不参与结果集
                jdbcTemplate.query(sql, (rs, rowNum) -> {
                    long id = rs.getLong("id");
                    String bucket = rs.getString("bucket");
                    String objectKey = rs.getString("object_key");
                    if (bucket != null && objectKey != null) {
                        urls.put(id, "http://192.168.100.136:9000/" + bucket + "/" + objectKey);
                    }
                    return null;
                }, batch.toArray());
            } catch (Exception e) {
                log.warn("Cross-db query educloud_file.file_object batch failed, skip this batch: {}", e.getMessage());
            }
        }
        return urls;
    }

    /**
     * 行映射：category_id / cover_file_id / enrollment_count 等可空列用 getObject 安全转换。
     */
    private CourseRow mapCourseRow(ResultSet rs, int rowNum) throws SQLException {
        CourseRow row = new CourseRow();
        row.setCourseId(rs.getLong("course_id"));
        row.setTitle(rs.getString("title"));
        row.setCategoryId(toLong(rs.getObject("category_id")));
        row.setCategoryName(rs.getString("category_name"));
        row.setPublishedAt(rs.getObject("published_at", LocalDateTime.class));
        row.setPrice(rs.getBigDecimal("price"));
        row.setCoverFileId(toLong(rs.getObject("cover_file_id")));
        row.setEnrollmentCount(toInteger(rs.getObject("enrollment_count")));
        row.setRatingAvg(rs.getBigDecimal("rating_avg"));
        return row;
    }

    private Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer toInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
