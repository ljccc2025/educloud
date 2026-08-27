package com.educloud.recommendation.service;

import com.educloud.recommendation.dto.response.RecommendationItem;
import com.educloud.recommendation.dto.response.RecommendationResponse;
import com.educloud.recommendation.entity.RecommendationRuleConfigEntity;
import com.educloud.recommendation.support.CrossDbCourseAccessor;
import com.educloud.recommendation.support.CrossDbCourseAccessor.CourseRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RecommendationService 单元测试（JUnit 5 + Mockito，无 Spring 上下文）：
 * 1. 已购 / DISLIKE 课程被排除，推荐不足 limit 时不越界；
 * 2. 相同输入两次调用输出完全一致的确定性排序（同分按 course_id 升序）；
 * 3. 匿名用户只产生 POPULAR / NEW，不触发已学课程上下文查询；
 * 4. 目标课程场景下同分类课程排在前面；双命中课程 reason/strategy 按 POPULAR > NEW > SIMILAR 优先级重写；
 * 5. rating 为 null 按 0 计分、空可见集、目标课程不可见跳过 SIMILAR、limit 钳制等边界场景。
 */
class RecommendationServiceTest {

    private CrossDbCourseAccessor accessor;
    private RuleConfigService configService;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        accessor = mock(CrossDbCourseAccessor.class);
        configService = mock(RuleConfigService.class);
        when(configService.getEnabledRules()).thenReturn(List.of(
                rule("POPULAR", 40), rule("NEW", 30), rule("SIMILAR", 30)));
        when(configService.allocateQuota(anyInt())).thenReturn(Map.of(
                RuleConfigService.POPULAR, 4,
                RuleConfigService.NEW, 3,
                RuleConfigService.SIMILAR, 3));
        when(accessor.findCoverUrls(anySet())).thenReturn(Map.of());
        service = new RecommendationService(accessor, configService);
    }

    private RecommendationRuleConfigEntity rule(String key, int weight) {
        RecommendationRuleConfigEntity e = new RecommendationRuleConfigEntity();
        e.setRuleKey(key);
        e.setEnabled(true);
        e.setWeight(weight);
        e.setConfigVersion(1);
        return e;
    }

    private CourseRow row(long id, String title, long categoryId, String categoryName,
                          LocalDateTime publishedAt, int enrollment, String rating) {
        CourseRow r = new CourseRow();
        r.setCourseId(id);
        r.setTitle(title);
        r.setCategoryId(categoryId);
        r.setCategoryName(categoryName);
        r.setPublishedAt(publishedAt);
        r.setPrice(new BigDecimal("99.00"));
        r.setCoverFileId(id * 100L);
        r.setEnrollmentCount(enrollment);
        r.setRatingAvg(new BigDecimal(rating));
        return r;
    }

    /** 重载：ratingAvg 为 null（无评分）的课程 */
    private CourseRow row(long id, String title, long categoryId, String categoryName,
                          LocalDateTime publishedAt, int enrollment) {
        CourseRow r = row(id, title, categoryId, categoryName, publishedAt, enrollment, "0.0");
        r.setRatingAvg(null);
        return r;
    }

    private Set<Long> itemIds(RecommendationResponse response) {
        return response.getItems().stream()
                .map(i -> Long.parseLong(i.getCourseId()))
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("已购与 DISLIKE 课程被排除，候选不足 limit 时结果不越界且 reason 非空")
    void excludesEnrolledAndDislikedCourses() {
        // 6 门可见课程：1 已购、2 已 DISLIKE、3-6 为候选
        List<CourseRow> visible = List.of(
                row(1L, "已购课程", 1L, "后端开发", LocalDateTime.of(2026, 1, 1, 0, 0), 100, "4.9"),
                row(2L, "不感兴趣课程", 1L, "后端开发", LocalDateTime.of(2026, 1, 2, 0, 0), 90, "4.8"),
                row(3L, "候选3", 1L, "后端开发", LocalDateTime.of(2026, 1, 3, 0, 0), 10, "4.0"),
                row(4L, "候选4", 1L, "后端开发", LocalDateTime.of(2026, 1, 4, 0, 0), 20, "4.2"),
                row(5L, "候选5", 1L, "后端开发", LocalDateTime.of(2026, 1, 5, 0, 0), 30, "4.5"),
                row(6L, "候选6", 1L, "后端开发", LocalDateTime.of(2026, 1, 6, 0, 0), 40, "4.8"));
        when(accessor.findVisibleCourses()).thenReturn(visible);
        when(accessor.findEnrolledCourseIds(100L)).thenReturn(List.of(1L));
        when(accessor.findEnrolledCourseContexts(100L)).thenReturn(List.of());

        RecommendationResponse response = service.recommend(100L, null, 10, Set.of(2L));

        assertEquals(4, response.getItems().size());
        Set<Long> ids = itemIds(response);
        assertFalse(ids.contains(1L), "已购课程应被排除");
        assertFalse(ids.contains(2L), "DISLIKE 课程应被排除");
        assertTrue(response.getItems().stream()
                        .allMatch(i -> i.getReason() != null && !i.getReason().isBlank()),
                "所有条目 reason 必须非空");
    }

    @Test
    @DisplayName("相同输入两次调用输出顺序完全一致（同分按 course_id 数值升序）")
    void deterministicOrderForSameInput() {
        List<CourseRow> visible = List.of(
                row(11L, "课程11", 1L, "后端开发", LocalDateTime.of(2026, 1, 1, 0, 0), 30, "4.5"),
                row(22L, "课程22", 1L, "后端开发", LocalDateTime.of(2026, 2, 1, 0, 0), 30, "4.5"));
        when(accessor.findVisibleCourses()).thenReturn(visible);

        RecommendationResponse first = service.recommend(null, null, 10, Set.of());
        RecommendationResponse second = service.recommend(null, null, 10, Set.of());

        List<String> firstOrder = first.getItems().stream()
                .map(RecommendationItem::getCourseId).collect(Collectors.toList());
        List<String> secondOrder = second.getItems().stream()
                .map(RecommendationItem::getCourseId).collect(Collectors.toList());
        assertEquals(firstOrder, secondOrder);
        assertEquals(List.of("11", "22"), firstOrder);
    }

    @Test
    @DisplayName("匿名用户只产生 POPULAR / NEW，不调用已学课程上下文查询")
    void anonymousGetsPopularAndNewOnly() {
        List<CourseRow> visible = List.of(
                row(31L, "课程31", 1L, "后端开发", LocalDateTime.of(2026, 1, 3, 0, 0), 10, "4.0"),
                row(32L, "课程32", 2L, "前端开发", LocalDateTime.of(2026, 1, 2, 0, 0), 20, "4.2"),
                row(33L, "课程33", 3L, "人工智能", LocalDateTime.of(2026, 1, 1, 0, 0), 30, "4.5"));
        when(accessor.findVisibleCourses()).thenReturn(visible);

        RecommendationResponse response = service.recommend(null, null, 10, Set.of());

        assertEquals(3, response.getItems().size());
        assertTrue(response.getItems().stream()
                        .noneMatch(i -> RuleConfigService.SIMILAR.equals(i.getStrategy())),
                "匿名推荐不应包含 SIMILAR 策略条目");
        verify(accessor, never()).findEnrolledCourseContexts(any());
        verify(accessor, never()).findEnrolledCourseIds(any());
    }

    @Test
    @DisplayName("目标课程场景：双命中课程（热门+同类目）标签按 POPULAR 最高优先级重写")
    void courseContextUsesTargetCategory() {
        List<CourseRow> visible = List.of(
                row(100L, "目标课程", 1L, "后端开发", LocalDateTime.of(2026, 1, 1, 0, 0), 300, "4.5"),
                row(101L, "同类高分", 1L, "后端开发", LocalDateTime.of(2026, 1, 2, 0, 0), 500, "4.9"),
                row(102L, "前端新课", 2L, "前端开发", LocalDateTime.of(2026, 8, 1, 0, 0), 50, "4.5"),
                row(103L, "前端旧课", 2L, "前端开发", LocalDateTime.of(2026, 7, 1, 0, 0), 10, "4.0"),
                row(104L, "同类次高分", 1L, "后端开发", LocalDateTime.of(2026, 1, 3, 0, 0), 400, "4.8"));
        when(accessor.findVisibleCourses()).thenReturn(visible);

        RecommendationResponse response = service.recommend(null, 100L, 6, Set.of());

        assertEquals(4, response.getItems().size());
        // 全局按热门度降序：101（399 分）与 104（328 分）必然排在前两名
        RecommendationItem top = response.getItems().get(0);
        RecommendationItem second = response.getItems().get(1);
        assertEquals("101", top.getCourseId());
        // 101/104 同时命中 POPULAR（高分）与 SIMILAR（与目标课程同类目）：按规格 5.2 第 6 步
        // 优先级 POPULAR > NEW > SIMILAR，双命中课程标签必须重写为 POPULAR / 热门课程
        assertEquals(RuleConfigService.POPULAR, top.getStrategy());
        assertEquals("热门课程", top.getReason());
        assertEquals("104", second.getCourseId());
        assertEquals(RuleConfigService.POPULAR, second.getStrategy());
        assertEquals("热门课程", second.getReason());
    }

    @Test
    @DisplayName("rating 为 null 按 0 计分：高 enrollment 课程排前")
    void ratingNullScoresZero() {
        List<CourseRow> visible = List.of(
                row(200L, "课程A", 1L, "后端开发", LocalDateTime.of(2026, 1, 1, 0, 0), 100),
                row(201L, "课程B", 1L, "后端开发", LocalDateTime.of(2026, 1, 2, 0, 0), 10, "5.0"));
        when(accessor.findVisibleCourses()).thenReturn(visible);

        RecommendationResponse response = service.recommend(null, null, 10, Set.of());

        // A：100×0.7 + 0 = 70；B：10×0.7 + 5.0×10 = 57 → 匿名推荐中 A 必须排在 B 前
        assertEquals(List.of("200", "201"), response.getItems().stream()
                .map(RecommendationItem::getCourseId).collect(Collectors.toList()));
    }

    @Test
    @DisplayName("可见课程为空：返回空 items 且不抛异常")
    void emptyVisibleReturnsEmpty() {
        when(accessor.findVisibleCourses()).thenReturn(List.of());

        RecommendationResponse response = service.recommend(null, null, 10, Set.of());

        assertTrue(response.getItems().isEmpty());
    }

    @Test
    @DisplayName("目标课程不在可见集：跳过 SIMILAR 策略")
    void targetCourseNotVisibleSkipsSimilar() {
        List<CourseRow> visible = List.of(
                row(301L, "课程301", 1L, "后端开发", LocalDateTime.of(2026, 1, 1, 0, 0), 100, "4.5"),
                row(302L, "课程302", 1L, "后端开发", LocalDateTime.of(2026, 1, 2, 0, 0), 90, "4.5"));
        when(accessor.findVisibleCourses()).thenReturn(visible);

        RecommendationResponse response = service.recommend(null, 999L, 10, Set.of());

        assertEquals(2, response.getItems().size());
        assertTrue(response.getItems().stream()
                        .noneMatch(i -> RuleConfigService.SIMILAR.equals(i.getStrategy())),
                "目标课程不可见时不应产生 SIMILAR 条目");
    }

    @Test
    @DisplayName("limit 越界钳制：0 与 21 均不抛异常且结果不超过 20 条")
    void limitClampedToOneToTwenty() {
        List<CourseRow> visible = new ArrayList<>();
        for (long i = 1; i <= 30; i++) {
            visible.add(row(i, "课程" + i, 1L, "后端开发",
                    LocalDateTime.of(2026, 1, 1, 0, 0).plusDays(i), (int) (i * 10), "4.0"));
        }
        when(accessor.findVisibleCourses()).thenReturn(visible);

        RecommendationResponse r0 = service.recommend(null, null, 0, Set.of());
        RecommendationResponse r21 = service.recommend(null, null, 21, Set.of());

        assertTrue(r0.getItems().size() >= 1 && r0.getItems().size() <= 20,
                "limit=0 钳制为 1，结果应在 1~20 之间");
        assertEquals(20, r21.getItems().size(), "limit=21 按上限 20 返回");
    }

    @Test
    @DisplayName("双命中课程：reason/strategy 按 POPULAR > NEW > SIMILAR 优先级重写")
    void dualHitUsesHighestPriorityStrategy() {
        // 511 同时命中 POPULAR（enrollment 极高）与 SIMILAR（与已购课程同分类）→ 必须重写为 POPULAR
        when(accessor.findEnrolledCourseIds(500L)).thenReturn(List.of(510L));
        when(accessor.findEnrolledCourseContexts(500L)).thenReturn(List.of(
                row(510L, "已学课程", 1L, "后端开发", LocalDateTime.of(2025, 12, 1, 0, 0), 50, "4.0")));
        // 缩小配额，制造「窗口小于候选数」的场景，验证优先级重写的各个分支
        when(configService.allocateQuota(anyInt())).thenReturn(Map.of(
                RuleConfigService.POPULAR, 1,
                RuleConfigService.NEW, 1,
                RuleConfigService.SIMILAR, 2));
        List<CourseRow> visible = List.of(
                row(511L, "双命中课程", 1L, "后端开发", LocalDateTime.of(2026, 1, 2, 0, 0), 1000, "5.0"),
                row(512L, "同类低热课程", 1L, "后端开发", LocalDateTime.of(2026, 1, 1, 0, 0), 5, "3.0"),
                row(513L, "最新热门课程", 2L, "前端开发", LocalDateTime.of(2026, 1, 5, 0, 0), 900, "4.0"),
                row(514L, "热门异类课程", 3L, "人工智能", LocalDateTime.of(2026, 1, 4, 0, 0), 850, "4.0"));
        when(accessor.findVisibleCourses()).thenReturn(visible);

        RecommendationResponse response = service.recommend(500L, null, 4, Set.of());

        assertEquals(4, response.getItems().size());
        // 全局排序按 score 降序：511（750）→ 513（670）→ 514（635）→ 512（33.5）
        RecommendationItem dual = response.getItems().get(0);
        assertEquals("511", dual.getCourseId());
        assertEquals(RuleConfigService.POPULAR, dual.getStrategy());
        assertEquals("热门课程", dual.getReason());
        Map<String, RecommendationItem> byId = response.getItems().stream()
                .collect(Collectors.toMap(RecommendationItem::getCourseId, i -> i));
        // 未命中更高优先级窗口的课程保持原标签：NEW 保持新上架、SIMILAR-only 保持同属理由
        assertEquals(RuleConfigService.NEW, byId.get("513").getStrategy());
        assertEquals("新上架", byId.get("513").getReason());
        assertEquals(RuleConfigService.SIMILAR, byId.get("512").getStrategy());
        assertTrue(byId.get("512").getReason().startsWith("与你学习的《已学课程》"));
        assertEquals(RuleConfigService.POPULAR, byId.get("514").getStrategy());
        assertEquals("热门课程", byId.get("514").getReason());
    }
}
