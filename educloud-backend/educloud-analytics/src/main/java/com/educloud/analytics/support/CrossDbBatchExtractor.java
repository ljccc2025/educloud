package com.educloud.analytics.support;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 跨库事实抽取适配器
 * 负责从 MySQL 各逻辑库中分批只读拉取历史业务事实（用户注册、课程发布、订单支付）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossDbBatchExtractor {

    private final DataSource dataSource;

    @Data
    @Builder
    public static class UserFact {
        private Long userId;
        private LocalDate registerDate;
    }

    @Data
    @Builder
    public static class CourseFact {
        private String courseId;
        private String title;
        private String teacherId;
        private LocalDate publishDate;
    }

    @Data
    @Builder
    public static class OrderFact {
        private String orderNo;
        private String courseId;
        private String teacherId;
        private Long amountCents;
        private String status;
        private LocalDate orderDate;
    }

    public List<UserFact> extractUserFacts(int page, int pageSize) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try {
            int offset = page * pageSize;
            String sql = "SELECT id, DATE(created_at) AS reg_date FROM educloud_user.sys_user ORDER BY id ASC LIMIT ? OFFSET ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, pageSize, offset);
            List<UserFact> list = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Long id = ((Number) r.get("id")).longValue();
                Date d = (Date) r.get("reg_date");
                LocalDate date = (d != null) ? d.toLocalDate() : LocalDate.now();
                list.add(UserFact.builder().userId(id).registerDate(date).build());
            }
            return list;
        } catch (Exception e) {
            log.warn("Direct cross-db query to educloud_user fallback: {}", e.getMessage());
            return List.of();
        }
    }

    public List<CourseFact> extractCourseFacts(int page, int pageSize) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try {
            int offset = page * pageSize;
            String sql = "SELECT id, title, teacher_id, DATE(created_at) AS pub_date FROM educloud_course.course WHERE status = 'PUBLISHED' ORDER BY id ASC LIMIT ? OFFSET ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, pageSize, offset);
            List<CourseFact> list = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                String id = String.valueOf(r.get("id"));
                String title = (String) r.get("title");
                String teacherId = String.valueOf(r.get("teacher_id"));
                Date d = (Date) r.get("pub_date");
                LocalDate date = (d != null) ? d.toLocalDate() : LocalDate.now();
                list.add(CourseFact.builder().courseId(id).title(title).teacherId(teacherId).publishDate(date).build());
            }
            return list;
        } catch (Exception e) {
            log.warn("Direct cross-db query to educloud_course fallback: {}", e.getMessage());
            return List.of();
        }
    }

    public List<OrderFact> extractOrderFacts(int page, int pageSize) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try {
            int offset = page * pageSize;
            String sql = "SELECT order_no, course_id, teacher_id, total_amount_cents, status, DATE(created_at) AS ord_date FROM educloud_order.order_main WHERE status IN ('PAID', 'REFUNDED') ORDER BY id ASC LIMIT ? OFFSET ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, pageSize, offset);
            List<OrderFact> list = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                String orderNo = (String) r.get("order_no");
                String courseId = String.valueOf(r.get("course_id"));
                String teacherId = String.valueOf(r.get("teacher_id"));
                Long amount = ((Number) r.get("total_amount_cents")).longValue();
                String status = (String) r.get("status");
                Date d = (Date) r.get("ord_date");
                LocalDate date = (d != null) ? d.toLocalDate() : LocalDate.now();
                list.add(OrderFact.builder()
                        .orderNo(orderNo)
                        .courseId(courseId)
                        .teacherId(teacherId)
                        .amountCents(amount)
                        .status(status)
                        .orderDate(date)
                        .build());
            }
            return list;
        } catch (Exception e) {
            log.warn("Direct cross-db query to educloud_order fallback: {}", e.getMessage());
            return List.of();
        }
    }
}
