package com.educloud.course.state;

import com.educloud.common.error.BusinessException;
import com.educloud.course.exception.CourseErrorCode;

import java.util.Map;
import java.util.Set;

/**
 * 审核提交状态机（M05 任务 9）：PENDING→APPROVED/REJECTED/WITHDRAWN。
 *
 * <p>依据：规格 §6 —— course_audit_submission 状态 PENDING/APPROVED/REJECTED/WITHDRAWN，
 * 只有 PENDING 可被审批/驳回/撤回；其余转移抛 SUBMISSION_NOT_PENDING 409。
 * 终态（APPROVED/REJECTED/WITHDRAWN）无出边，保证"版本不可再审批"。</p>
 */
public final class AuditStateMachine {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String WITHDRAWN = "WITHDRAWN";

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            PENDING, Set.of(APPROVED, REJECTED, WITHDRAWN),
            APPROVED, Set.of(),
            REJECTED, Set.of(),
            WITHDRAWN, Set.of());

    private AuditStateMachine() {
    }

    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** 非法转移 → SUBMISSION_NOT_PENDING（409）。 */
    public static void requireTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(CourseErrorCode.SUBMISSION_NOT_PENDING,
                    "Audit submission is not in pending state: " + from + " -> " + to);
        }
    }
}
