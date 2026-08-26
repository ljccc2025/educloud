package com.educloud.search.support;

import com.educloud.search.document.CourseIndexDoc;
import com.educloud.search.document.LessonDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 课程与课件数据抽取器 (CourseDataExtractor)
 * 用于全量索引平滑重建时从关系型数据库分批提取已发布的课程及嵌套课件数据。
 * 支持单库/跨库 (educloud_course / educloud_content) 兼容与高健壮性容错保护。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseDataExtractor {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 统计所有处于 PUBLISHED 状态且已关联发布版本的课程总数
     *
     * @return 已发布课程总数
     */
    public int countPublishedCourses() {
        try {
            return doCount("course", "course_version");
        } catch (DataAccessException ex) {
            log.warn("Count published courses on local tables failed: {}. Trying educloud_course schema prefix...", ex.getMessage());
            try {
                return doCount("educloud_course.course", "educloud_course.course_version");
            } catch (Exception ex2) {
                log.error("Count published courses failed completely: {}", ex2.getMessage());
                return 0;
            }
        }
    }

    private int doCount(String courseTable, String versionTable) {
        String sql = "SELECT COUNT(*) FROM " + courseTable + " c " +
                "INNER JOIN " + versionTable + " v ON c.published_version_id = v.id " +
                "WHERE c.lifecycle_status = 'PUBLISHED'";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    /**
     * 分页抽取已发布课程及对应课件数据，并组装为 CourseIndexDoc 列表
     *
     * @param offset 偏移量
     * @param limit  单批次提取数量
     * @return 完整的 CourseIndexDoc 列表
     */
    public List<CourseIndexDoc> extractPublishedCourses(int offset, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        int safeOffset = Math.max(0, offset);

        List<RawCourseRow> rawCourses;
        try {
            rawCourses = doQueryCourses("course", "course_version", "course_category", safeOffset, limit);
        } catch (DataAccessException ex) {
            log.warn("Query courses on local tables failed: {}. Trying educloud_course schema prefix...", ex.getMessage());
            try {
                rawCourses = doQueryCourses("educloud_course.course", "educloud_course.course_version", "educloud_course.course_category", safeOffset, limit);
            } catch (Exception ex2) {
                log.error("Query courses failed completely: {}", ex2.getMessage());
                return Collections.emptyList();
            }
        }

        if (rawCourses.isEmpty()) {
            return Collections.emptyList();
        }

        // 提取所有课程 ID 用于批量获取课件
        List<Long> courseIds = rawCourses.stream().map(RawCourseRow::getCourseId).toList();
        Map<Long, List<LessonDoc>> lessonMap = extractLessonsForCourses(courseIds);

        // 组装完整的 CourseIndexDoc
        List<CourseIndexDoc> resultDocs = new ArrayList<>(rawCourses.size());
        for (RawCourseRow row : rawCourses) {
            String courseIdStr = String.valueOf(row.getCourseId());
            List<LessonDoc> lessons = lessonMap.getOrDefault(row.getCourseId(), Collections.emptyList());

            CourseIndexDoc doc = CourseIndexDoc.builder()
                    .id(courseIdStr)
                    .courseId(courseIdStr)
                    .title(row.getTitle())
                    .subtitle(row.getSubtitle())
                    .description(row.getDescription())
                    .teacherId(row.getTeacherId() != null ? String.valueOf(row.getTeacherId()) : null)
                    .teacherName(null)
                    .category(row.getCategoryName())
                    .categoryCode(row.getCategorySlug())
                    .coverUrl(null)
                    .difficulty(row.getLevel())
                    .priceCents(row.getPriceCents())
                    .isFree(row.getPriceCents() == 0L)
                    .rating(row.getRating())
                    .studentCount(row.getEnrollmentCount())
                    .lessonCount(lessons.size())
                    .status("PUBLISHED")
                    .tags(Collections.emptyList())
                    .lessons(lessons)
                    .aggregateVersion(row.getVersion())
                    .publishedAt(row.getPublishedAt())
                    .updatedAt(row.getUpdatedAt())
                    .build();

            resultDocs.add(doc);
        }

        return resultDocs;
    }

    private List<RawCourseRow> doQueryCourses(String courseTable, String versionTable, String categoryTable, int offset, int limit) {
        String sql = "SELECT c.id AS course_id, c.owner_teacher_id, c.lifecycle_status, c.published_at, " +
                "c.rating_avg, c.enrollment_count, c.updated_at, c.version, " +
                "v.title, v.subtitle, v.description, v.level, v.price, " +
                "cat.name AS category_name, cat.slug AS category_slug " +
                "FROM " + courseTable + " c " +
                "INNER JOIN " + versionTable + " v ON c.published_version_id = v.id " +
                "LEFT JOIN " + categoryTable + " cat ON v.category_id = cat.id " +
                "WHERE c.lifecycle_status = 'PUBLISHED' " +
                "ORDER BY c.id ASC " +
                "LIMIT ? OFFSET ?";

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRawCourseRow(rs), limit, offset);
    }

    private RawCourseRow mapRawCourseRow(ResultSet rs) throws SQLException {
        long courseId = rs.getLong("course_id");
        long teacherIdVal = rs.getLong("owner_teacher_id");
        Long teacherId = teacherIdVal > 0 ? teacherIdVal : null;

        BigDecimal price = rs.getBigDecimal("price");
        long priceCents = price != null ? price.multiply(BigDecimal.valueOf(100)).longValue() : 0L;

        BigDecimal ratingAvg = rs.getBigDecimal("rating_avg");
        float rating = ratingAvg != null ? ratingAvg.floatValue() : 0.0f;

        int enrollmentCount = rs.getInt("enrollment_count");
        long versionVal = rs.getLong("version");
        long version = versionVal > 0 ? versionVal : 1L;

        Timestamp publishedAtTs = rs.getTimestamp("published_at");
        LocalDateTime publishedAt = publishedAtTs != null ? publishedAtTs.toLocalDateTime() : LocalDateTime.now();

        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        LocalDateTime updatedAt = updatedAtTs != null ? updatedAtTs.toLocalDateTime() : LocalDateTime.now();

        return RawCourseRow.builder()
                .courseId(courseId)
                .teacherId(teacherId)
                .title(rs.getString("title"))
                .subtitle(rs.getString("subtitle"))
                .description(rs.getString("description"))
                .level(rs.getString("level"))
                .priceCents(priceCents)
                .categoryName(rs.getString("category_name"))
                .categorySlug(rs.getString("category_slug"))
                .rating(rating)
                .enrollmentCount(enrollmentCount)
                .version(version)
                .publishedAt(publishedAt)
                .updatedAt(updatedAt)
                .build();
    }

    private Map<Long, List<LessonDoc>> extractLessonsForCourses(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            return doQueryLessons("courseware", "chapter", courseIds);
        } catch (DataAccessException ex) {
            log.warn("Query lessons on local tables failed: {}. Trying educloud_content schema prefix...", ex.getMessage());
            try {
                return doQueryLessons("educloud_content.courseware", "educloud_content.chapter", courseIds);
            } catch (Exception ex2) {
                log.warn("Query lessons failed completely (content tables may be offline or empty): {}. Proceeding without lessons.", ex2.getMessage());
                return Collections.emptyMap();
            }
        }
    }

    private Map<Long, List<LessonDoc>> doQueryLessons(String coursewareTable, String chapterTable, List<Long> courseIds) {
        String placeholders = courseIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT cw.id AS lesson_id, cw.course_id, cw.title AS lesson_title, cw.free_preview, ch.title AS chapter_title " +
                "FROM " + coursewareTable + " cw " +
                "INNER JOIN " + chapterTable + " ch ON cw.chapter_id = ch.id " +
                "WHERE cw.course_id IN (" + placeholders + ") AND cw.status = 'ACTIVE' AND ch.status = 'ACTIVE' " +
                "ORDER BY ch.sort_order ASC, cw.sort_order ASC";

        Object[] params = courseIds.toArray();
        Map<Long, List<LessonDoc>> lessonMap = new HashMap<>();

        jdbcTemplate.query(sql, rs -> {
            long courseId = rs.getLong("course_id");
            long lessonId = rs.getLong("lesson_id");
            String lessonTitle = rs.getString("lesson_title");
            String chapterTitle = rs.getString("chapter_title");
            boolean isPreview = rs.getInt("free_preview") == 1;

            LessonDoc lessonDoc = LessonDoc.builder()
                    .id(String.valueOf(lessonId))
                    .title(lessonTitle)
                    .chapterTitle(chapterTitle)
                    .isPreview(isPreview)
                    .build();

            lessonMap.computeIfAbsent(courseId, k -> new ArrayList<>()).add(lessonDoc);
        }, params);

        return lessonMap;
    }

    @lombok.Data
    @lombok.Builder
    private static class RawCourseRow {
        private Long courseId;
        private Long teacherId;
        private String title;
        private String subtitle;
        private String description;
        private String level;
        private Long priceCents;
        private String categoryName;
        private String categorySlug;
        private Float rating;
        private Integer enrollmentCount;
        private Long version;
        private LocalDateTime publishedAt;
        private LocalDateTime updatedAt;
    }
}
