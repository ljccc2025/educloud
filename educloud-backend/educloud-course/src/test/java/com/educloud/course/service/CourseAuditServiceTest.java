package com.educloud.course.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.course.dto.response.CourseAuditResponse;
import com.educloud.course.entity.CourseAuditSubmissionEntity;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.entity.CourseVersionEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.AuditEventMapper;
import com.educloud.course.mapper.CourseAuditSubmissionMapper;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseTeacherMapper;
import com.educloud.course.mapper.CourseVersionMapper;
import com.educloud.course.messaging.CourseEventPublisher;
import com.educloud.course.observability.AuditWriter;
import com.educloud.course.observability.CourseMetrics;
import com.educloud.course.state.CourseVersionStateMachine;
import com.educloud.course.support.MybatisPlusTestSupport;
import com.educloud.course.support.TeacherAccessGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M05 任务 9：课程审核服务单元测试。
 *
 * <p>依据：规格 §6/§7 —— submit（归属+DRAFT→PENDING_REVIEW+写 submission）、
 * approve（锁根同事务切换 published_version_id、旧版 SUPERSEDED、lifecycle=PUBLISHED、
 * outbox CoursePublished）、reject（原因必填、REJECTED、draft 指针保留）、
 * withdraw（仅提交教师本人、PENDING→WITHDRAWN）；自审 403、非法转移 409。
 * Mockito 直接 mock 四个 Mapper + CourseEventPublisher；approve 的事务性以
 * 方法级 @Transactional 注解断言（同事务语义由 IT 用真实 MySQL 验证）。</p>
 */
@ExtendWith(MockitoExtension.class)
class CourseAuditServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisPlusTestSupport.registerTableInfo(
                CourseEntity.class,
                CourseVersionEntity.class,
                CourseAuditSubmissionEntity.class,
                CourseTeacherEntity.class);
    }

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private CourseVersionMapper courseVersionMapper;

    @Mock
    private CourseAuditSubmissionMapper submissionMapper;

    @Mock
    private CourseTeacherMapper courseTeacherMapper;

    @Mock
    private CourseEventPublisher eventPublisher;

    @Mock
    private AuditEventMapper auditEventMapper;

    @Mock
    private RequestContextAccessor requestContextAccessor;

    @Mock
    private CourseMetrics courseMetrics;

    @Mock
    private AuditWriter auditWriter;

    // ---------------------------------------------------------------- submit

    @Test
    void submitMovesDraftVersionToPendingReviewAndWritesPendingSubmission() throws Exception {
        CourseEntity course = course(101L, 1001L, 301L, null, "DRAFT", 5L);
        CourseVersionEntity version = version(301L, 101L, 2, "DRAFT", "待审标题");
        when(courseVersionMapper.selectById(301L)).thenReturn(version);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        when(courseVersionMapper.update(isNull(), any())).thenReturn(1);
        assignSubmissionId(401L);
        when(courseMapper.updateById(any(CourseEntity.class))).thenReturn(1);

        CourseAuditResponse response = auditService().submitForReview(301L, 1001L, Set.of("TEACHER"));

        assertThat(response.auditId()).isEqualTo("401");
        assertThat(response.courseId()).isEqualTo("101");
        assertThat(response.versionId()).isEqualTo("301");
        assertThat(response.submissionStatus()).isEqualTo("PENDING");
        assertThat(response.versionStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(response.lifecycleStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(response.submittedBy()).isEqualTo("1001");
        assertThat(response.submittedAt()).isNotNull();
        assertThat(response.title()).isEqualTo("待审标题");
        verify(auditWriter).write("SUBMIT_FOR_REVIEW", "course_version", "301",
                1001L, Set.of("TEACHER"), "SUCCESS", null);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaUpdateWrapper> versionCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(courseVersionMapper).update(isNull(), versionCaptor.capture());
        LambdaUpdateWrapper<CourseVersionEntity> wrapper = versionCaptor.getValue();
        assertThat(wrapper.getSqlSet()).contains("version_status=");
        assertThat(wrapper.getSqlSegment()).contains("id =").contains("version_status =");

        ArgumentCaptor<CourseAuditSubmissionEntity> submissionCaptor =
                ArgumentCaptor.forClass(CourseAuditSubmissionEntity.class);
        verify(submissionMapper).insert(submissionCaptor.capture());
        CourseAuditSubmissionEntity inserted = submissionCaptor.getValue();
        assertThat(inserted.getCourseId()).isEqualTo(101L);
        assertThat(inserted.getCourseVersionId()).isEqualTo(301L);
        assertThat(inserted.getStatus()).isEqualTo("PENDING");
        assertThat(inserted.getSubmittedBy()).isEqualTo(1001L);
        assertThat(inserted.getSubmittedAt()).isNotNull();

        ArgumentCaptor<CourseEntity> courseCaptor = ArgumentCaptor.forClass(CourseEntity.class);
        verify(courseMapper).updateById(courseCaptor.capture());
        assertThat(courseCaptor.getValue().getLifecycleStatus()).isEqualTo("PENDING_REVIEW");

        java.lang.reflect.Method submit = CourseAuditService.class.getDeclaredMethod(
                "submitForReview", Long.class, Long.class, Set.class);
        assertThat(submit.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void submitRejectsNonDraftVersionWith409() {
        CourseVersionEntity version = version(301L, 101L, 1, "PENDING_REVIEW", "待审");
        when(courseVersionMapper.selectById(301L)).thenReturn(version);
        when(courseMapper.selectById(101L)).thenReturn(course(101L, 1001L, 301L, null, "PENDING_REVIEW", 1L));
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> auditService().submitForReview(301L, 1001L, Set.of("TEACHER")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.VERSION_NOT_DRAFT);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(submissionMapper, never()).insert(any(CourseAuditSubmissionEntity.class));
        verify(courseVersionMapper, never()).update(any(), any());
    }

    @Test
    void submitRejectsCrossTeacherAccessWith403() {
        CourseVersionEntity version = version(301L, 101L, 1, "DRAFT", "他人草稿");
        when(courseVersionMapper.selectById(301L)).thenReturn(version);
        when(courseMapper.selectById(101L)).thenReturn(course(101L, 2002L, 301L, null, "DRAFT", 1L));
        when(courseTeacherMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> auditService().submitForReview(301L, 1001L, Set.of("TEACHER")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED));
        verify(courseVersionMapper, never()).update(any(), any());
        verify(submissionMapper, never()).insert(any(CourseAuditSubmissionEntity.class));
    }

    @Test
    void submitReturns404WhenVersionNotFound() {
        when(courseVersionMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> auditService().submitForReview(999L, 1001L, Set.of("TEACHER")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FOUND));
    }

    @Test
    void submitRejectsVersionNotPointedByCourseDraftPointer() {
        CourseVersionEntity version = version(301L, 101L, 1, "DRAFT", "孤儿草稿");
        when(courseVersionMapper.selectById(301L)).thenReturn(version);
        when(courseMapper.selectById(101L)).thenReturn(course(101L, 1001L, 399L, null, "DRAFT", 1L));
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> auditService().submitForReview(301L, 1001L, Set.of("TEACHER")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.VERSION_NOT_DRAFT);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(submissionMapper, never()).insert(any(CourseAuditSubmissionEntity.class));
    }

    @Test
    void submitRejectsArchivedWith409() {
        // 归档终态门禁（规格 §15）：残留 DRAFT 指针的归档课程也不可提交审核（防御
        // 残留指针绕过 republish 复活）→ COURSE_STATE_CONFLICT 409。
        CourseVersionEntity version = version(301L, 101L, 1, "DRAFT", "归档残留草稿");
        when(courseVersionMapper.selectById(301L)).thenReturn(version);
        when(courseMapper.selectById(101L)).thenReturn(course(101L, 1001L, 301L, null, "ARCHIVED", 1L));
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> auditService().submitForReview(301L, 1001L, Set.of("TEACHER")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_STATE_CONFLICT);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(courseVersionMapper, never()).update(any(), any());
        verify(submissionMapper, never()).insert(any(CourseAuditSubmissionEntity.class));
    }

    // ---------------------------------------------------------------- approve

    @Test
    void approveLocksCourseAndPublishesVersionWithOldVersionSuperseded() throws Exception {
        CourseEntity course = course(101L, 1001L, 302L, 301L, "PENDING_REVIEW", 7L);
        CourseVersionEntity pending = version(302L, 101L, 2, "PENDING_REVIEW", "新版标题");
        CourseAuditSubmissionEntity submission = submission(401L, 101L, 302L, "PENDING", 1001L);
        when(submissionMapper.selectById(401L)).thenReturn(submission);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseVersionMapper.selectById(302L)).thenReturn(pending);
        when(courseVersionMapper.update(isNull(), any())).thenReturn(1);
        when(submissionMapper.updateById(any(CourseAuditSubmissionEntity.class))).thenReturn(1);
        // 模拟 OptimisticLockerInnerInterceptor 对 update(entity, wrapper) 的回写行为：
        // entity 携带旧 version 进入，拦截器自动递增并回写（服务内禁止手动 +1）。
        doAnswer(invocation -> {
            CourseEntity entity = invocation.getArgument(0);
            entity.setVersion(entity.getVersion() + 1);
            return 1;
        }).when(courseMapper).update(any(CourseEntity.class), any());

        CourseAuditResponse response = auditService().approve(401L, 3001L, Set.of("SYSTEM_ADMIN"));

        assertThat(response.submissionStatus()).isEqualTo("APPROVED");
        assertThat(response.versionStatus()).isEqualTo("PUBLISHED");
        assertThat(response.lifecycleStatus()).isEqualTo("PUBLISHED");
        assertThat(response.reviewedBy()).isEqualTo("3001");
        assertThat(response.reviewedAt()).isNotNull();
        verify(courseMetrics).recordCoursePublished();
        verify(courseMetrics).recordAuditApproved();
        verify(auditWriter).write("AUDIT_APPROVED", "course_audit", "401",
                3001L, Set.of("SYSTEM_ADMIN"), "SUCCESS", null);

        verify(courseMapper).selectByIdForUpdate(101L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaUpdateWrapper> versionCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(courseVersionMapper, times(2)).update(isNull(), versionCaptor.capture());
        @SuppressWarnings("rawtypes")
        List<LambdaUpdateWrapper> wrappers = versionCaptor.getAllValues();
        // 旧发布版本 → SUPERSEDED（WHERE id=301 AND version_status=PUBLISHED）。
        assertThat(wrappers.get(0).getSqlSegment()).contains("id =").contains("version_status =");
        assertThat(wrappers.get(0).getParamNameValuePairs())
                .containsValue("SUPERSEDED").containsValue(301L);
        // 新版本 → PUBLISHED（WHERE id=302 AND version_status=PENDING_REVIEW）。
        assertThat(wrappers.get(1).getSqlSegment()).contains("id =").contains("version_status =");
        assertThat(wrappers.get(1).getParamNameValuePairs())
                .containsValue("PUBLISHED").containsValue(302L);

        ArgumentCaptor<CourseAuditSubmissionEntity> submissionCaptor =
                ArgumentCaptor.forClass(CourseAuditSubmissionEntity.class);
        verify(submissionMapper).updateById(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getStatus()).isEqualTo("APPROVED");
        assertThat(submissionCaptor.getValue().getReviewedBy()).isEqualTo(3001L);
        assertThat(submissionCaptor.getValue().getReviewedAt()).isNotNull();

        // 根更新走 update(entity, wrapper)：wrapper 显式 SET draft_version_id=null（
        // NOT_NULL 策略下 updateById 无法清空），entity 携带 version 由拦截器回写 +1。
        ArgumentCaptor<CourseEntity> courseCaptor = ArgumentCaptor.forClass(CourseEntity.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaUpdateWrapper> rootWrapperCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(courseMapper).update(courseCaptor.capture(), rootWrapperCaptor.capture());
        CourseEntity updated = courseCaptor.getValue();
        assertThat(updated.getPublishedVersionId()).isEqualTo(302L);
        assertThat(updated.getLifecycleStatus()).isEqualTo("PUBLISHED");
        assertThat(updated.getPublishedAt()).isNotNull();
        assertThat(updated.getDraftVersionId()).isNull();
        assertThat(updated.getVersion()).isEqualTo(8L);
        assertThat(rootWrapperCaptor.getValue().getSqlSet()).contains("draft_version_id=");
        assertThat(rootWrapperCaptor.getValue().getSqlSegment()).contains("id =");

        verify(eventPublisher).coursePublished(101L, 302L, 8L, updated.getPublishedAt());

        java.lang.reflect.Method approve = CourseAuditService.class.getDeclaredMethod(
                "approve", Long.class, Long.class, Set.class);
        assertThat(approve.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void approveRejectsSelfReviewWith403() {
        CourseAuditSubmissionEntity submission = submission(401L, 101L, 302L, "PENDING", 3001L);
        when(submissionMapper.selectById(401L)).thenReturn(submission);

        assertThatThrownBy(() -> auditService().approve(401L, 3001L, Set.of("SYSTEM_ADMIN")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(403);
                });
        verify(courseMapper, never()).selectByIdForUpdate(any());
        verify(courseVersionMapper, never()).update(any(), any());
        verify(eventPublisher, never()).coursePublished(any(), any(), anyLong(), any());
    }

    @Test
    void approveRejectsNonPendingSubmissionWith409() {
        CourseAuditSubmissionEntity submission = submission(401L, 101L, 302L, "APPROVED", 1001L);
        when(submissionMapper.selectById(401L)).thenReturn(submission);

        assertThatThrownBy(() -> auditService().approve(401L, 3001L, Set.of("SYSTEM_ADMIN")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.SUBMISSION_NOT_PENDING);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void approveRejectsNonPendingVersionWith409() {
        CourseAuditSubmissionEntity submission = submission(401L, 101L, 302L, "PENDING", 1001L);
        when(submissionMapper.selectById(401L)).thenReturn(submission);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course(101L, 1001L, null, null, "PENDING_REVIEW", 1L));
        when(courseVersionMapper.selectById(302L)).thenReturn(version(302L, 101L, 2, "REJECTED", "已驳回"));

        assertThatThrownBy(() -> auditService().approve(401L, 3001L, Set.of("SYSTEM_ADMIN")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.VERSION_NOT_DRAFT);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(courseVersionMapper, never()).update(any(), any());
        verify(eventPublisher, never()).coursePublished(any(), any(), anyLong(), any());
    }

    @Test
    void approveRejectsArchivedWith409() {
        // 归档终态门禁（规格 §15）：即使存在 PENDING 提交，归档课程也不得审批发布
        // （与 submit 构成复活路径双保险）→ COURSE_STATE_CONFLICT 409。
        CourseAuditSubmissionEntity submission = submission(401L, 101L, 302L, "PENDING", 1001L);
        when(submissionMapper.selectById(401L)).thenReturn(submission);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course(101L, 1001L, 302L, null, "ARCHIVED", 1L));

        assertThatThrownBy(() -> auditService().approve(401L, 3001L, Set.of("SYSTEM_ADMIN")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_STATE_CONFLICT);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(courseVersionMapper, never()).update(any(), any());
        verify(submissionMapper, never()).updateById(any(CourseAuditSubmissionEntity.class));
        verify(eventPublisher, never()).coursePublished(any(), any(), anyLong(), any());
    }

    @Test
    void approveMapsRootOptimisticLockMissTo409() {
        CourseEntity course = course(101L, 1001L, 302L, null, "PENDING_REVIEW", 7L);
        CourseVersionEntity pending = version(302L, 101L, 2, "PENDING_REVIEW", "新版");
        CourseAuditSubmissionEntity submission = submission(401L, 101L, 302L, "PENDING", 1001L);
        when(submissionMapper.selectById(401L)).thenReturn(submission);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseVersionMapper.selectById(302L)).thenReturn(pending);
        when(courseVersionMapper.update(isNull(), any())).thenReturn(1);
        when(submissionMapper.updateById(any(CourseAuditSubmissionEntity.class))).thenReturn(1);
        when(courseMapper.update(any(CourseEntity.class), any())).thenReturn(0);

        assertThatThrownBy(() -> auditService().approve(401L, 3001L, Set.of("SYSTEM_ADMIN")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.VERSION_CONFLICT));
        verify(eventPublisher, never()).coursePublished(any(), any(), anyLong(), any());
    }

    // ---------------------------------------------------------------- reject

    @Test
    void rejectRequiresReasonWith400() {
        // 原因校验先于任何查询/写操作（空原因直接 400）。
        assertThatThrownBy(() -> auditService().reject(401L, 3001L, "  ", Set.of("SYSTEM_ADMIN")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.REVIEW_REJECT_REASON_REQUIRED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(400);
                });
        verify(submissionMapper, never()).updateById(any(CourseAuditSubmissionEntity.class));
    }

    @Test
    void rejectMarksSubmissionAndVersionRejectedAndKeepsDraftPointer() {
        CourseEntity course = course(101L, 1001L, 302L, null, "PENDING_REVIEW", 6L);
        CourseVersionEntity pending = version(302L, 101L, 2, "PENDING_REVIEW", "待驳回");
        CourseAuditSubmissionEntity submission = submission(401L, 101L, 302L, "PENDING", 1001L);
        when(submissionMapper.selectById(401L)).thenReturn(submission);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseVersionMapper.selectById(302L)).thenReturn(pending);
        when(submissionMapper.updateById(any(CourseAuditSubmissionEntity.class))).thenReturn(1);
        when(courseVersionMapper.update(isNull(), any())).thenReturn(1);
        when(courseMapper.updateById(any(CourseEntity.class))).thenReturn(1);

        CourseAuditResponse response = auditService().reject(401L, 3001L, "内容不完整", Set.of("SYSTEM_ADMIN"));

        assertThat(response.submissionStatus()).isEqualTo("REJECTED");
        assertThat(response.versionStatus()).isEqualTo("REJECTED");
        assertThat(response.reason()).isEqualTo("内容不完整");
        assertThat(response.reviewedBy()).isEqualTo("3001");
        assertThat(response.reviewedAt()).isNotNull();
        verify(courseMetrics).recordAuditRejected();
        verify(auditWriter).write("AUDIT_REJECTED", "course_audit", "401",
                3001L, Set.of("SYSTEM_ADMIN"), "SUCCESS", "内容不完整");

        ArgumentCaptor<CourseAuditSubmissionEntity> submissionCaptor =
                ArgumentCaptor.forClass(CourseAuditSubmissionEntity.class);
        verify(submissionMapper).updateById(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getStatus()).isEqualTo("REJECTED");
        assertThat(submissionCaptor.getValue().getReason()).isEqualTo("内容不完整");
        assertThat(submissionCaptor.getValue().getReviewedBy()).isEqualTo(3001L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaUpdateWrapper> versionCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(courseVersionMapper).update(isNull(), versionCaptor.capture());
        assertThat(versionCaptor.getValue().getParamNameValuePairs()).containsValue("REJECTED");

        ArgumentCaptor<CourseEntity> courseCaptor = ArgumentCaptor.forClass(CourseEntity.class);
        verify(courseMapper).updateById(courseCaptor.capture());
        // 驳回后 draft 指针保留在 REJECTED 版本（可复制新草稿），lifecycle 回到 DRAFT。
        assertThat(courseCaptor.getValue().getDraftVersionId()).isEqualTo(302L);
        assertThat(courseCaptor.getValue().getLifecycleStatus()).isEqualTo("DRAFT");
    }

    @Test
    void rejectRejectsSelfReviewWith403() {
        CourseAuditSubmissionEntity submission = submission(401L, 101L, 302L, "PENDING", 3001L);
        when(submissionMapper.selectById(401L)).thenReturn(submission);

        assertThatThrownBy(() -> auditService().reject(401L, 3001L, "理由", Set.of("SYSTEM_ADMIN")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED));
        verify(submissionMapper, never()).updateById(any(CourseAuditSubmissionEntity.class));
    }

    @Test
    void rejectRejectsNonPendingSubmissionWith409() {
        CourseAuditSubmissionEntity submission = submission(401L, 101L, 302L, "WITHDRAWN", 1001L);
        when(submissionMapper.selectById(401L)).thenReturn(submission);

        assertThatThrownBy(() -> auditService().reject(401L, 3001L, "理由", Set.of("SYSTEM_ADMIN")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.SUBMISSION_NOT_PENDING));
    }

    // ---------------------------------------------------------------- withdraw

    @Test
    void withdrawAllowsOnlySubmittingTeacherAndMarksWithdrawn() {
        CourseEntity course = course(101L, 1001L, 302L, null, "PENDING_REVIEW", 4L);
        CourseVersionEntity pending = version(302L, 101L, 2, "PENDING_REVIEW", "待撤回");
        CourseAuditSubmissionEntity submission = submission(401L, 101L, 302L, "PENDING", 1001L);
        when(submissionMapper.selectById(401L)).thenReturn(submission);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseVersionMapper.selectById(302L)).thenReturn(pending);
        when(submissionMapper.updateById(any(CourseAuditSubmissionEntity.class))).thenReturn(1);
        when(courseVersionMapper.update(isNull(), any())).thenReturn(1);
        when(courseMapper.updateById(any(CourseEntity.class))).thenReturn(1);

        CourseAuditResponse response = auditService().withdraw(401L, 1001L, Set.of("TEACHER"));

        assertThat(response.submissionStatus()).isEqualTo("WITHDRAWN");
        assertThat(response.versionStatus()).isEqualTo("WITHDRAWN");
        assertThat(response.withdrawnAt()).isNotNull();
        verify(auditWriter).write("SUBMISSION_WITHDRAWN", "course_audit", "401",
                1001L, Set.of("TEACHER"), "SUCCESS", null);

        ArgumentCaptor<CourseAuditSubmissionEntity> submissionCaptor =
                ArgumentCaptor.forClass(CourseAuditSubmissionEntity.class);
        verify(submissionMapper).updateById(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getStatus()).isEqualTo("WITHDRAWN");
        assertThat(submissionCaptor.getValue().getWithdrawnAt()).isNotNull();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaUpdateWrapper> versionCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(courseVersionMapper).update(isNull(), versionCaptor.capture());
        assertThat(versionCaptor.getValue().getParamNameValuePairs()).containsValue("WITHDRAWN");

        // 任务 22 规格审查：撤回后 draft 指针清空 → 编辑页 GET draft 404，走重建草稿恢复路径。
        ArgumentCaptor<CourseEntity> courseCaptor = ArgumentCaptor.forClass(CourseEntity.class);
        verify(courseMapper).updateById(courseCaptor.capture());
        assertThat(courseCaptor.getValue().getDraftVersionId()).isNull();
        assertThat(courseCaptor.getValue().getLifecycleStatus()).isEqualTo("DRAFT");
    }

    @Test
    void withdrawClearsDraftPointerForFirstTimeSubmission() {
        // 首次提交即撤回：无发布版本（publishedVersionId=null），撤回后指针清空，
        // 编辑页可经 POST /courses/{id}/drafts 从 WITHDRAWN 版本重建草稿（见 Draft 服务测试）。
        CourseEntity course = course(101L, 1001L, 302L, null, "PENDING_REVIEW", 4L);
        CourseVersionEntity pending = version(302L, 101L, 1, "PENDING_REVIEW", "首次提交");
        CourseAuditSubmissionEntity submission = submission(401L, 101L, 302L, "PENDING", 1001L);
        when(submissionMapper.selectById(401L)).thenReturn(submission);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseVersionMapper.selectById(302L)).thenReturn(pending);
        when(submissionMapper.updateById(any(CourseAuditSubmissionEntity.class))).thenReturn(1);
        when(courseVersionMapper.update(isNull(), any())).thenReturn(1);
        when(courseMapper.updateById(any(CourseEntity.class))).thenReturn(1);

        auditService().withdraw(401L, 1001L, Set.of("TEACHER"));

        ArgumentCaptor<CourseEntity> courseCaptor = ArgumentCaptor.forClass(CourseEntity.class);
        verify(courseMapper).updateById(courseCaptor.capture());
        assertThat(courseCaptor.getValue().getDraftVersionId()).isNull();
        assertThat(courseCaptor.getValue().getPublishedVersionId()).isNull();
        assertThat(courseCaptor.getValue().getLifecycleStatus()).isEqualTo("DRAFT");
    }

    @Test
    void withdrawRejectsNonSubmitterWith403() {
        CourseAuditSubmissionEntity submission = submission(401L, 101L, 302L, "PENDING", 1001L);
        when(submissionMapper.selectById(401L)).thenReturn(submission);

        assertThatThrownBy(() -> auditService().withdraw(401L, 9999L, Set.of("TEACHER")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(403);
                });
        verify(submissionMapper, never()).updateById(any(CourseAuditSubmissionEntity.class));
        verify(courseVersionMapper, never()).update(any(), any());
    }

    @Test
    void withdrawRejectsNonPendingSubmissionWith409() {
        CourseAuditSubmissionEntity submission = submission(401L, 101L, 302L, "REJECTED", 1001L);
        when(submissionMapper.selectById(401L)).thenReturn(submission);

        assertThatThrownBy(() -> auditService().withdraw(401L, 1001L, Set.of("TEACHER")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.SUBMISSION_NOT_PENDING));
    }

    // ---------------------------------------------------------------- list/detail

    @Test
    void listPendingReturnsSubmissionsNewestFirstWithSnapshots() {
        Page<CourseAuditSubmissionEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(submission(401L, 101L, 302L, "PENDING", 1001L)));
        page.setTotal(1);
        when(submissionMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(courseMapper.selectById(101L)).thenReturn(course(101L, 1001L, 302L, null, "PENDING_REVIEW", 1L));
        when(courseVersionMapper.selectById(302L)).thenReturn(version(302L, 101L, 2, "PENDING_REVIEW", "待审列表"));

        var result = auditService().listPending(1, 10);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(10);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).submissionStatus()).isEqualTo("PENDING");
        assertThat(result.items().get(0).title()).isEqualTo("待审列表");
        assertThat(result.items().get(0).versionId()).isEqualTo("302");
    }

    @Test
    void getDetailReturnsSubmissionSnapshotWithCourseAndVersion() {
        when(submissionMapper.selectById(401L)).thenReturn(submission(401L, 101L, 302L, "REJECTED", 1001L));
        when(courseMapper.selectById(101L)).thenReturn(course(101L, 1001L, 302L, null, "DRAFT", 1L));
        when(courseVersionMapper.selectById(302L)).thenReturn(version(302L, 101L, 2, "REJECTED", "驳回快照"));

        CourseAuditResponse response = auditService().getDetail(401L);

        assertThat(response.auditId()).isEqualTo("401");
        assertThat(response.submissionStatus()).isEqualTo("REJECTED");
        assertThat(response.versionStatus()).isEqualTo("REJECTED");
        assertThat(response.lifecycleStatus()).isEqualTo("DRAFT");
        assertThat(response.title()).isEqualTo("驳回快照");
    }

    @Test
    void getDetailReturns404WhenSubmissionNotFound() {
        when(submissionMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> auditService().getDetail(999L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FOUND));
    }

    // ---------------------------------------------------------------- helpers

    private CourseAuditService auditService() {
        return new CourseAuditService(
                courseMapper,
                courseVersionMapper,
                submissionMapper,
                new TeacherAccessGuard(courseTeacherMapper),
                eventPublisher,
                courseMetrics,
                auditWriter);
    }

    private void assignSubmissionId(Long id) {
        doAnswer(invocation -> {
            CourseAuditSubmissionEntity entity = invocation.getArgument(0);
            entity.setId(id);
            return 1;
        }).when(submissionMapper).insert(any(CourseAuditSubmissionEntity.class));
    }

    private static CourseEntity course(
            Long id, Long ownerTeacherId, Long draftVersionId, Long publishedVersionId,
            String lifecycle, Long version) {
        CourseEntity entity = new CourseEntity();
        entity.setId(id);
        entity.setOwnerTeacherId(ownerTeacherId);
        entity.setDraftVersionId(draftVersionId);
        entity.setPublishedVersionId(publishedVersionId);
        entity.setLifecycleStatus(lifecycle);
        entity.setVersion(version);
        return entity;
    }

    private static CourseVersionEntity version(Long id, Long courseId, int versionNo, String status, String title) {
        CourseVersionEntity entity = new CourseVersionEntity();
        entity.setId(id);
        entity.setCourseId(courseId);
        entity.setVersionNo(versionNo);
        entity.setVersionStatus(status);
        entity.setTitle(title);
        entity.setCategoryId(5L);
        entity.setLevel("BEGINNER");
        entity.setPrice(new BigDecimal("99.00"));
        entity.setCurrency("CNY");
        entity.setCreatedAt(LocalDateTime.of(2026, 8, 23, 9, 0));
        return entity;
    }

    private static CourseAuditSubmissionEntity submission(
            Long id, Long courseId, Long versionId, String status, Long submittedBy) {
        CourseAuditSubmissionEntity entity = new CourseAuditSubmissionEntity();
        entity.setId(id);
        entity.setCourseId(courseId);
        entity.setCourseVersionId(versionId);
        entity.setStatus(status);
        entity.setSubmittedBy(submittedBy);
        entity.setSubmittedAt(LocalDateTime.of(2026, 8, 23, 10, 0));
        return entity;
    }
}
