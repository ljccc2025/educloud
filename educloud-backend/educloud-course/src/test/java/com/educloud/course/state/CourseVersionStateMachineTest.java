package com.educloud.course.state;

import com.educloud.common.error.BusinessException;
import com.educloud.course.exception.CourseErrorCode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M05 任务 9：课程版本状态机与审核状态机单元测试。
 *
 * <p>依据：规格 §6/§7 —— 版本 DRAFT→PENDING_REVIEW→REJECTED/PUBLISHED→SUPERSEDED、
 * PENDING_REVIEW→WITHDRAWN；审核提交 PENDING→APPROVED/REJECTED/WITHDRAWN。
 * 非法转移分别抛 VERSION_NOT_DRAFT 409 / SUBMISSION_NOT_PENDING 409（任务 9 关键实现点）。</p>
 */
class CourseVersionStateMachineTest {

    @Nested
    class CourseVersionTransitions {

        @Test
        void allowsAllSpecifiedTransitions() {
            assertThatCode(() -> CourseVersionStateMachine.requireTransition(
                    CourseVersionStateMachine.DRAFT, CourseVersionStateMachine.PENDING_REVIEW))
                    .doesNotThrowAnyException();
            assertThatCode(() -> CourseVersionStateMachine.requireTransition(
                    CourseVersionStateMachine.PENDING_REVIEW, CourseVersionStateMachine.REJECTED))
                    .doesNotThrowAnyException();
            assertThatCode(() -> CourseVersionStateMachine.requireTransition(
                    CourseVersionStateMachine.PENDING_REVIEW, CourseVersionStateMachine.PUBLISHED))
                    .doesNotThrowAnyException();
            assertThatCode(() -> CourseVersionStateMachine.requireTransition(
                    CourseVersionStateMachine.PUBLISHED, CourseVersionStateMachine.SUPERSEDED))
                    .doesNotThrowAnyException();
            assertThatCode(() -> CourseVersionStateMachine.requireTransition(
                    CourseVersionStateMachine.PENDING_REVIEW, CourseVersionStateMachine.WITHDRAWN))
                    .doesNotThrowAnyException();
        }

        @Test
        void reportsReachableTransitions() {
            assertThat(CourseVersionStateMachine.canTransition(
                    CourseVersionStateMachine.DRAFT, CourseVersionStateMachine.PENDING_REVIEW)).isTrue();
            assertThat(CourseVersionStateMachine.canTransition(
                    CourseVersionStateMachine.PENDING_REVIEW, CourseVersionStateMachine.PUBLISHED)).isTrue();
            assertThat(CourseVersionStateMachine.canTransition(
                    CourseVersionStateMachine.PENDING_REVIEW, CourseVersionStateMachine.WITHDRAWN)).isTrue();
            assertThat(CourseVersionStateMachine.canTransition(
                    CourseVersionStateMachine.DRAFT, CourseVersionStateMachine.WITHDRAWN)).isFalse();
        }

        @Test
        void rejectsIllegalDraftTransitionsWith409() {
            assertIllegal(CourseVersionStateMachine.DRAFT, CourseVersionStateMachine.PUBLISHED);
            assertIllegal(CourseVersionStateMachine.DRAFT, CourseVersionStateMachine.REJECTED);
            assertIllegal(CourseVersionStateMachine.DRAFT, CourseVersionStateMachine.SUPERSEDED);
            // 规格：WITHDRAWN 是提交后撤回，DRAFT 无撤回概念 → 不允许。
            assertIllegal(CourseVersionStateMachine.DRAFT, CourseVersionStateMachine.WITHDRAWN);
        }

        @Test
        void rejectsIllegalPendingReviewTransitionsWith409() {
            assertIllegal(CourseVersionStateMachine.PENDING_REVIEW, CourseVersionStateMachine.DRAFT);
            assertIllegal(CourseVersionStateMachine.PENDING_REVIEW, CourseVersionStateMachine.SUPERSEDED);
            assertIllegal(CourseVersionStateMachine.PENDING_REVIEW, CourseVersionStateMachine.PENDING_REVIEW);
        }

        @Test
        void rejectsIllegalPublishedAndTerminalTransitionsWith409() {
            assertIllegal(CourseVersionStateMachine.PUBLISHED, CourseVersionStateMachine.REJECTED);
            assertIllegal(CourseVersionStateMachine.PUBLISHED, CourseVersionStateMachine.WITHDRAWN);
            assertIllegal(CourseVersionStateMachine.PUBLISHED, CourseVersionStateMachine.PENDING_REVIEW);
            assertIllegal(CourseVersionStateMachine.REJECTED, CourseVersionStateMachine.DRAFT);
            assertIllegal(CourseVersionStateMachine.REJECTED, CourseVersionStateMachine.PENDING_REVIEW);
            assertIllegal(CourseVersionStateMachine.SUPERSEDED, CourseVersionStateMachine.PUBLISHED);
            assertIllegal(CourseVersionStateMachine.WITHDRAWN, CourseVersionStateMachine.PENDING_REVIEW);
            assertIllegal(CourseVersionStateMachine.WITHDRAWN, CourseVersionStateMachine.DRAFT);
        }

        @Test
        void rejectsNullSourceWith409() {
            assertIllegal(null, CourseVersionStateMachine.PENDING_REVIEW);
        }

        private void assertIllegal(String from, String to) {
            assertThatThrownBy(() -> CourseVersionStateMachine.requireTransition(from, to))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.VERSION_NOT_DRAFT);
                        assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                    });
        }
    }

    @Nested
    class AuditTransitions {

        @Test
        void allowsAllSpecifiedTransitions() {
            assertThatCode(() -> AuditStateMachine.requireTransition(
                    AuditStateMachine.PENDING, AuditStateMachine.APPROVED))
                    .doesNotThrowAnyException();
            assertThatCode(() -> AuditStateMachine.requireTransition(
                    AuditStateMachine.PENDING, AuditStateMachine.REJECTED))
                    .doesNotThrowAnyException();
            assertThatCode(() -> AuditStateMachine.requireTransition(
                    AuditStateMachine.PENDING, AuditStateMachine.WITHDRAWN))
                    .doesNotThrowAnyException();
        }

        @Test
        void rejectsIllegalTransitionsWith409() {
            assertIllegal(AuditStateMachine.PENDING, AuditStateMachine.PENDING);
            assertIllegal(AuditStateMachine.APPROVED, AuditStateMachine.REJECTED);
            assertIllegal(AuditStateMachine.APPROVED, AuditStateMachine.WITHDRAWN);
            assertIllegal(AuditStateMachine.REJECTED, AuditStateMachine.APPROVED);
            assertIllegal(AuditStateMachine.WITHDRAWN, AuditStateMachine.APPROVED);
            assertIllegal(null, AuditStateMachine.APPROVED);
        }

        private void assertIllegal(String from, String to) {
            assertThatThrownBy(() -> AuditStateMachine.requireTransition(from, to))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.SUBMISSION_NOT_PENDING);
                        assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                    });
        }
    }
}
