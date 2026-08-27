package com.educloud.recommendation.service;

import com.educloud.recommendation.dto.response.RecommendationItem;
import com.educloud.recommendation.dto.response.RecommendationResponse;
import com.educloud.recommendation.entity.RecommendationRuleConfigEntity;
import com.educloud.recommendation.support.CrossDbCourseAccessor;
import com.educloud.recommendation.support.CrossDbCourseAccessor.CourseRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final BigDecimal RATING_WEIGHT = BigDecimal.TEN;
    private static final BigDecimal ENROLL_WEIGHT = new BigDecimal("0.7");

    private final CrossDbCourseAccessor accessor;
    private final RuleConfigService configService;

    /** 生成推荐列表。固定输入 → 固定输出。 */
    public RecommendationResponse recommend(Long userId, Long courseId, int limit, Set<Long> disliked) {
        int safeLimit = Math.max(1, Math.min(20, limit));
        // configVersion 取自规则实体（getEnabledRules 本地缓存 60s，不重复查库）；规则缺失/版本为空兜底 1
        List<RecommendationRuleConfigEntity> rules = configService.getEnabledRules();
        Integer configVersion = rules.isEmpty() ? 1
                : (rules.get(0).getConfigVersion() != null ? rules.get(0).getConfigVersion() : 1);
        List<CourseRow> visible = accessor.findVisibleCourses();
        if (visible.isEmpty()) {
            return RecommendationResponse.builder().configVersion(configVersion).items(List.of()).build();
        }
        Map<Long, CourseRow> visibleById = visible.stream()
                .collect(Collectors.toMap(CourseRow::getCourseId, Function.identity()));
        Map<Long, String> coverUrls = accessor.findCoverUrls(
                visible.stream().map(CourseRow::getCoverFileId)
                        .filter(Objects::nonNull).collect(Collectors.toSet()));

        Set<Long> excluded = new HashSet<>(disliked == null ? Set.of() : disliked);
        if (userId != null) {
            excluded.addAll(accessor.findEnrolledCourseIds(userId));
        }
        if (courseId != null) {
            excluded.add(courseId);
        }
        List<CourseRow> candidates = visible.stream()
                .filter(r -> !excluded.contains(r.getCourseId()))
                .toList();

        List<RecommendationItem> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();

        // 同类目候选（登录且已购 / 相关课程场景）；目标课程不在可见集时跳过 SIMILAR
        Set<Long> similarCategoryIds = new HashSet<>();
        String similarReason = null;
        if (courseId != null) {
            CourseRow target = courseId == null ? null : visibleById.get(courseId);
            if (target != null) {
                similarCategoryIds.add(target.getCategoryId());
                similarReason = "与本课程同属「" + target.getCategoryName() + "」";
            }
        } else if (userId != null) {
            List<CourseRow> contexts = accessor.findEnrolledCourseContexts(userId);
            if (!contexts.isEmpty()) {
                similarCategoryIds.addAll(contexts.stream()
                        .map(CourseRow::getCategoryId).collect(Collectors.toSet()));
                CourseRow first = contexts.get(0);
                similarReason = "与你学习的《" + first.getTitle() + "》同属「" + first.getCategoryName() + "」";
            }
        }

        List<CourseRow> similarCandidates = candidates.stream()
                .filter(r -> similarCategoryIds.contains(r.getCategoryId()))
                .sorted(Comparator.comparing(this::popularScore).reversed()
                        .thenComparing(CourseRow::getCourseId))
                .toList();

        Map<String, Integer> quota = configService.allocateQuota(safeLimit);
        int similarQuota = quota.getOrDefault(RuleConfigService.SIMILAR, 0);
        int newQuota = quota.getOrDefault(RuleConfigService.NEW, 0);
        int popularQuota = quota.getOrDefault(RuleConfigService.POPULAR, 0);

        List<CourseRow> byNew = candidates.stream()
                .sorted(Comparator.comparing(CourseRow::getPublishedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CourseRow::getCourseId))
                .toList();
        List<CourseRow> byPopular = candidates.stream()
                .sorted(Comparator.comparing(this::popularScore).reversed()
                        .thenComparing(CourseRow::getCourseId))
                .toList();

        // 双命中重写窗口：在 take 之前按各策略排名前缀计算配额窗口；hitWindow 与 take 使用
        // 相同的「跳过已用课程再计数」语义（此处 used 尚未被任何 take 填充，窗口即排名前缀）
        Set<Long> popularIds = hitWindow(byPopular, popularQuota, used);
        Set<Long> newIds = hitWindow(byNew, newQuota, used);

        take(similarCandidates, similarQuota, RuleConfigService.SIMILAR, similarReason, result, used, coverUrls);
        take(byNew, newQuota, RuleConfigService.NEW, "新上架", result, used, coverUrls);
        take(byPopular, popularQuota, RuleConfigService.POPULAR, "热门课程", result, used, coverUrls);

        // 空缺补齐：按 POPULAR → NEW 顺序补足 safeLimit
        for (CourseRow row : byPopular) {
            if (result.size() >= safeLimit) break;
            if (used.add(row.getCourseId())) {
                result.add(toItem(row, "热门课程", RuleConfigService.POPULAR, coverUrls));
            }
        }
        for (CourseRow row : byNew) {
            if (result.size() >= safeLimit) break;
            if (used.add(row.getCourseId())) {
                result.add(toItem(row, "新上架", RuleConfigService.NEW, coverUrls));
            }
        }

        // 双命中重写（规格 5.2 第 6 步）：同一课程命中多策略只保留一次，reason/strategy 按
        // POPULAR > NEW > SIMILAR 优先级取最高者。take 顺序 SIMILAR→NEW→POPULAR 会让双命中课程
        // 被低优先级策略先占用，此处按「各策略配额窗口」（排名前缀）重写为最高优先级标签：
        // 在 POPULAR 窗口内 → 热门课程；否则在 NEW 窗口内 → 新上架；否则保持原标签（通常为 SIMILAR）。
        for (RecommendationItem item : result) {
            long id = Long.parseLong(item.getCourseId());
            if (popularIds.contains(id)) {
                item.setStrategy(RuleConfigService.POPULAR);
                item.setReason("热门课程");
            } else if (newIds.contains(id)) {
                item.setStrategy(RuleConfigService.NEW);
                item.setReason("新上架");
            }
        }

        // 最终确定性排序（规格 5.2 第 6 步）：score 降序，同分按 course_id 数值升序
        // 注意：scoreMap 为每个候选课程预计算的 popularScore，避免 RecommendationItem 无评分数
        Map<Long, BigDecimal> scoreMap = candidates.stream()
                .collect(Collectors.toMap(CourseRow::getCourseId, this::popularScore));
        result.sort(Comparator.comparing((RecommendationItem item) ->
                        scoreMap.getOrDefault(Long.parseLong(item.getCourseId()), BigDecimal.ZERO))
                .reversed()
                .thenComparing(item -> Long.parseLong(item.getCourseId())));

        return RecommendationResponse.builder()
                .configVersion(configVersion)
                .items(result)
                .build();
    }

    /** 策略命中窗口：排名列表中配额内的课程 ID 前缀（即该策略配额窗口的命中集合）。
     *  与 take 相同的 used 计数语义：跳过 used 中已存在的课程再计数；窗口在 take 之前
     *  计算（此时 used 为空），因此窗口即各策略的排名前缀。 */
    private Set<Long> hitWindow(List<CourseRow> rows, int quota, Set<Long> used) {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < rows.size() && ids.size() < quota; i++) {
            CourseRow row = rows.get(i);
            if (!used.contains(row.getCourseId())) {
                ids.add(row.getCourseId());
            }
        }
        return ids;
    }

    /** 热门度打分：enrollment_count × 0.7 + rating_avg × 10（rating_avg 为 null 按 0） */
    BigDecimal popularScore(CourseRow row) {
        BigDecimal enroll = BigDecimal.valueOf(
                row.getEnrollmentCount() == null ? 0 : row.getEnrollmentCount());
        BigDecimal rating = row.getRatingAvg() == null
                ? BigDecimal.ZERO : row.getRatingAvg();
        return enroll.multiply(ENROLL_WEIGHT).add(rating.multiply(RATING_WEIGHT));
    }

    private void take(List<CourseRow> rows, int quota, String strategy,
                      String reason, List<RecommendationItem> result, Set<Long> used,
                      Map<Long, String> coverUrls) {
        int count = 0;
        for (CourseRow row : rows) {
            if (count >= quota) break;
            if (used.add(row.getCourseId())) {
                result.add(toItem(row, reason, strategy, coverUrls));
                count++;
            }
        }
    }

    private RecommendationItem toItem(CourseRow row, String reason, String strategy,
                                      Map<Long, String> coverUrls) {
        return RecommendationItem.builder()
                .courseId(String.valueOf(row.getCourseId()))
                .title(row.getTitle())
                .categoryId(String.valueOf(row.getCategoryId()))
                .categoryName(row.getCategoryName())
                .coverUrl(row.getCoverFileId() == null ? "" : coverUrls.getOrDefault(row.getCoverFileId(), ""))
                .price(row.getPrice() == null ? "0.00" : row.getPrice().setScale(2, RoundingMode.HALF_UP).toPlainString())
                .reason(reason)
                .strategy(strategy)
                .build();
    }
}
