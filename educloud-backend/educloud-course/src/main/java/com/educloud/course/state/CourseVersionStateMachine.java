package com.educloud.course.state;

import com.educloud.common.error.BusinessException;
import com.educloud.course.exception.CourseErrorCode;

import java.util.Map;
import java.util.Set;

/**
 * 课程版本状态机（M05 任务 9）：DRAFT→PENDING_REVIEW→REJECTED/PUBLISHED→SUPERSEDED、
 * PENDING_REVIEW→WITHDRAWN。
 *
 * <p>依据：规格 §6/§7 与任务 9 关键实现点 —— WITHDRAWN 是提交后撤回，DRAFT 无撤回
 * 概念，故只允许 PENDING_REVIEW→WITHDRAWN；其余非法转移抛 VERSION_NOT_DRAFT 409。
 * 状态机是服务层业务硬规则的前置校验，并发兜底由条件更新（WHERE version_status=…）
 * 在服务实现中完成。</p>
 */
public final class CourseVersionStateMachine {

    public static final String DRAFT = "DRAFT";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String REJECTED = "REJECTED";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String SUPERSEDED = "SUPERSEDED";
    public static final String WITHDRAWN = "WITHDRAWN";

    /** 转移表：from → 允许的 to 集合。终态（REJECTED/SUPERSEDED/WITHDRAWN）无出边。 */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            DRAFT, Set.of(PENDING_REVIEW),
            PENDING_REVIEW, Set.of(REJECTED, PUBLISHED, WITHDRAWN),
            PUBLISHED, Set.of(SUPERSEDED),
            REJECTED, Set.of(),
            SUPERSEDED, Set.of(),
            WITHDRAWN, Set.of());

    private CourseVersionStateMachine() {
    }

    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** 非法转移 → VERSION_NOT_DRAFT（409）。 */
    public static void requireTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(CourseErrorCode.VERSION_NOT_DRAFT,
                    "Illegal course version state transition: " + from + " -> " + to);
        }
    }
}
