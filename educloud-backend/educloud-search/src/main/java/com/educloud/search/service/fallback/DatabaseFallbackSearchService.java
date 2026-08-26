package com.educloud.search.service.fallback;

import com.educloud.search.dto.request.CourseSearchQuery;
import com.educloud.search.dto.response.CourseSearchItem;
import com.educloud.search.dto.response.CourseSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 数据库兜底搜索服务 (Fallback Search)
 * 当 Elasticsearch 离线、超时或发生不可恢复异常时，降级使用 MySQL 进行轻量模糊检索。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseFallbackSearchService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 执行降级模糊检索
     *
     * @param query 搜索查询请求
     * @return 包含 isDegraded=true 的降级搜索响应
     */
    public CourseSearchResponse fallbackSearch(CourseSearchQuery query) {
        int page = query.getPage();
        int size = query.getSize();

        try {
            return doQuery(query, "course", "course_version");
        } catch (DataAccessException ex) {
            log.warn("Database fallback query on local tables failed: {}. Trying educloud_course schema prefix...", ex.getMessage());
            try {
                return doQuery(query, "educloud_course.course", "educloud_course.course_version");
            } catch (DataAccessException ex2) {
                log.error("Database fallback search failed completely: {}. Returning empty degraded response.", ex2.getMessage());
                return CourseSearchResponse.degraded(0L, page, size, Collections.emptyList());
            }
        } catch (Exception e) {
            log.error("Unexpected error in database fallback search: {}", e.getMessage(), e);
            return CourseSearchResponse.degraded(0L, page, size, Collections.emptyList());
        }
    }

    private CourseSearchResponse doQuery(CourseSearchQuery query, String courseTable, String versionTable) {
        int page = query.getPage();
        int size = query.getSize();
        int offset = (page - 1) * size;

        StringBuilder whereSql = new StringBuilder(" WHERE c.lifecycle_status = 'PUBLISHED' ");
        List<Object> params = new ArrayList<>();

        // 关键词模糊匹配
        if (StringUtils.hasText(query.getKeyword())) {
            String keywordPattern = "%" + query.getKeyword().trim() + "%";
            whereSql.append(" AND (v.title LIKE ? OR v.subtitle LIKE ? OR v.description LIKE ?) ");
            params.add(keywordPattern);
            params.add(keywordPattern);
            params.add(keywordPattern);
        }

        // 难度筛选
        if (StringUtils.hasText(query.getDifficulty())) {
            whereSql.append(" AND v.level = ? ");
            params.add(query.getDifficulty().trim());
        }

        // 免费筛选
        if (Boolean.TRUE.equals(query.getIsFree())) {
            whereSql.append(" AND v.price = 0 ");
        } else if (Boolean.FALSE.equals(query.getIsFree())) {
            whereSql.append(" AND v.price > 0 ");
        }

        // 价格区间筛选（分转元）
        if (query.getMinPriceCents() != null && query.getMinPriceCents() > 0) {
            BigDecimal minPrice = BigDecimal.valueOf(query.getMinPriceCents()).divide(BigDecimal.valueOf(100));
            whereSql.append(" AND v.price >= ? ");
            params.add(minPrice);
        }
        if (query.getMaxPriceCents() != null && query.getMaxPriceCents() > 0) {
            BigDecimal maxPrice = BigDecimal.valueOf(query.getMaxPriceCents()).divide(BigDecimal.valueOf(100));
            whereSql.append(" AND v.price <= ? ");
            params.add(maxPrice);
        }

        // 查询总记录数
        String countSql = "SELECT COUNT(*) FROM " + courseTable + " c " +
                "INNER JOIN " + versionTable + " v ON c.published_version_id = v.id " +
                whereSql;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        if (total == null || total == 0L) {
            return CourseSearchResponse.degraded(0L, page, size, Collections.emptyList());
        }

        // 排序规则
        String orderByClause = resolveOrderBy(query.getSortBy());

        // 查询分页列表
        String selectSql = "SELECT c.id AS course_id, c.owner_teacher_id, c.lifecycle_status, c.published_at, " +
                "c.rating_avg, c.enrollment_count, c.updated_at, " +
                "v.title, v.subtitle, v.description, v.level, v.price " +
                "FROM " + courseTable + " c " +
                "INNER JOIN " + versionTable + " v ON c.published_version_id = v.id " +
                whereSql +
                orderByClause +
                " LIMIT ? OFFSET ?";

        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(size);
        queryParams.add(offset);

        List<CourseSearchItem> items = jdbcTemplate.query(selectSql, new CourseSearchItemRowMapper(), queryParams.toArray());
        return CourseSearchResponse.degraded(total, page, size, items);
    }

    private String resolveOrderBy(String sortBy) {
        if (sortBy == null) {
            return " ORDER BY c.rating_avg DESC, c.enrollment_count DESC ";
        }
        return switch (sortBy.toLowerCase()) {
            case "popular" -> " ORDER BY c.enrollment_count DESC, c.rating_avg DESC ";
            case "newest" -> " ORDER BY c.published_at DESC ";
            case "price_asc" -> " ORDER BY v.price ASC, c.published_at DESC ";
            case "price_desc" -> " ORDER BY v.price DESC, c.published_at DESC ";
            default -> " ORDER BY c.rating_avg DESC, c.enrollment_count DESC ";
        };
    }

    private static class CourseSearchItemRowMapper implements RowMapper<CourseSearchItem> {
        @Override
        public CourseSearchItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            long courseIdVal = rs.getLong("course_id");
            String courseIdStr = String.valueOf(courseIdVal);
            long teacherIdVal = rs.getLong("owner_teacher_id");
            String teacherIdStr = teacherIdVal > 0 ? String.valueOf(teacherIdVal) : null;

            BigDecimal price = rs.getBigDecimal("price");
            long priceCents = price != null ? price.multiply(BigDecimal.valueOf(100)).longValue() : 0L;
            boolean isFree = priceCents == 0L;

            BigDecimal ratingAvg = rs.getBigDecimal("rating_avg");
            float rating = ratingAvg != null ? ratingAvg.floatValue() : 0.0f;

            Timestamp publishedAtTs = rs.getTimestamp("published_at");
            LocalDateTime publishedAt = publishedAtTs != null ? publishedAtTs.toLocalDateTime() : null;

            Timestamp updatedAtTs = rs.getTimestamp("updated_at");
            LocalDateTime updatedAt = updatedAtTs != null ? updatedAtTs.toLocalDateTime() : null;

            return CourseSearchItem.builder()
                    .id(courseIdStr)
                    .courseId(courseIdStr)
                    .title(rs.getString("title"))
                    .subtitle(rs.getString("subtitle"))
                    .description(rs.getString("description"))
                    .teacherId(teacherIdStr)
                    .difficulty(rs.getString("level"))
                    .priceCents(priceCents)
                    .isFree(isFree)
                    .rating(rating)
                    .studentCount(rs.getInt("enrollment_count"))
                    .lessonCount(0)
                    .status(rs.getString("lifecycle_status"))
                    .tags(Collections.emptyList())
                    .publishedAt(publishedAt)
                    .updatedAt(updatedAt)
                    .score(1.0)
                    .build();
        }
    }
}
