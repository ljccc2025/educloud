package com.educloud.course.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.course.dto.request.ReviewUpsertRequest;
import com.educloud.course.dto.response.CourseReviewResponse;
import com.educloud.course.entity.CourseEnrollmentEntity;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseReviewEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.CourseEnrollmentMapper;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseReviewMapper;
import com.educloud.course.mapper.CourseReviewSummaryRow;
import com.educloud.course.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M05 任务 14：课程评价服务单元测试。
 *
 * <p>依据：规格 §6/§7 —— POST /courses/{id}/reviews：课程存在（selectByIdForUpdate）→
 * ACTIVE 选课校验（未选课/REVOKED → 403 NOT_ENROLLED）→ INSERT ... ON DUPLICATE
 * （uk(course_id, student_id)）→ 同事务按 VISIBLE 评价重算 course.rating_avg/rating_count
 * （HIDDEN 不计入）；DELETE /course-reviews/{id}：管理角色（JWT roles claim 含
 * SYSTEM_ADMIN/SUPER_ADMIN，权限码无 review 专用码）→ 置 HIDDEN（软隐藏保留审计）→
 * 重算；已隐藏重复删幂等返回现状；rating 越界 → 400 VALIDATION_FAILED（服务层兜底，
 * 控制器 @Valid 负责 HTTP 入口）。隐藏为窄更新（仅 status/updated_by/updated_at
 * WHERE id=?），学生并发改分不被陈旧实体整行回写覆盖（P1b 竞态修复，含并发单测）。</p>
 */
@ExtendWith(MockitoExtension.class)
class CourseReviewServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisPlusTestSupport.registerTableInfo(
                CourseEntity.class,
                CourseEnrollmentEntity.class,
                CourseReviewEntity.class);
    }

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private CourseEnrollmentMapper enrollmentMapper;

    @Mock
    private CourseReviewMapper reviewMapper;

    private CourseReviewService service() {
        return new CourseReviewService(courseMapper, enrollmentMapper, reviewMapper);
    }

    // ---------------------------------------------------------------- upsert：拒绝路径

    @Test
    void upsertRejectsNotEnrolledStudentWith403() {
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course(101L, 7L));

        assertThatThrownBy(() -> service().upsert(101L, 5001L, new ReviewUpsertRequest(5, "好课")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.NOT_ENROLLED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(403);
                });
        verify(reviewMapper, never()).upsert(any());
        verify(courseMapper, never()).updateRatingSummary(anyLong(), any(), anyInt());
    }

    @Test
    void upsertRejectsRevokedEnrollmentWith403() {
        // REVOKED 行被 ACTIVE 过滤（wrapper 带 status=ACTIVE），selectCount=0 → 403。
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course(101L, 7L));
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service().upsert(101L, 5001L, new ReviewUpsertRequest(4, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.NOT_ENROLLED));
        verify(reviewMapper, never()).upsert(any());
    }

    @Test
    void upsertRejectsOutOfRangeRatingWith400() {
        // List.of 不允许 null 元素，rating=null（缺失）单独覆盖（Arrays.asList 允许）。
        for (Integer rating : java.util.Arrays.asList(0, 6, null)) {
            assertThatThrownBy(() -> service().upsert(101L, 5001L, new ReviewUpsertRequest(rating, "x")))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED);
                        assertThat(exception.errorCode().httpStatus()).isEqualTo(400);
                    });
        }
        verify(courseMapper, never()).selectByIdForUpdate(anyLong());
    }

    @Test
    void upsertReturns404WhenCourseNotFound() {
        when(courseMapper.selectByIdForUpdate(999L)).thenReturn(null);

        assertThatThrownBy(() -> service().upsert(999L, 5001L, new ReviewUpsertRequest(5, "x")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FOUND));
        verify(enrollmentMapper, never()).selectCount(any());
    }

    // ---------------------------------------------------------------- upsert：新评/更新

    @Test
    void upsertInsertsNewVisibleReviewAndRecalculatesSummary() throws Exception {
        CourseEntity course = course(101L, 7L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(enrollmentMapper.selectCount(any())).thenReturn(1L);
        when(reviewMapper.upsert(any())).thenReturn(1);
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 24, 10, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 24, 10, 0);
        when(reviewMapper.selectOne(any())).thenReturn(
                review(501L, 101L, 5001L, 5, "好课", "VISIBLE", createdAt, updatedAt));
        when(reviewMapper.selectVisibleSummary(101L))
                .thenReturn(summary(new BigDecimal("4.50"), 12L));
        when(courseMapper.updateRatingSummary(101L, new BigDecimal("4.50"), 12)).thenReturn(1);

        CourseReviewResponse response = service().upsert(101L, 5001L, new ReviewUpsertRequest(5, "好课"));

        assertThat(response.id()).isEqualTo("501");
        assertThat(response.studentId()).isEqualTo("5001");
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.content()).isEqualTo("好课");
        assertThat(response.status()).isEqualTo("VISIBLE");
        assertThat(response.createdAt()).isEqualTo(createdAt);

        ArgumentCaptor<CourseReviewEntity> captor = ArgumentCaptor.forClass(CourseReviewEntity.class);
        verify(reviewMapper).upsert(captor.capture());
        CourseReviewEntity upserted = captor.getValue();
        assertThat(upserted.getCourseId()).isEqualTo(101L);
        assertThat(upserted.getStudentId()).isEqualTo(5001L);
        assertThat(upserted.getRating()).isEqualTo(5);
        assertThat(upserted.getContent()).isEqualTo("好课");
        assertThat(upserted.getStatus()).isEqualTo("VISIBLE");
        assertThat(upserted.getCreatedBy()).isEqualTo(5001L);
        assertThat(upserted.getUpdatedBy()).isEqualTo(5001L);
        assertThat(upserted.getId()).isNotNull();

        // 同事务重算：以 VISIBLE 评价聚合更新 course 汇总列。
        verify(courseMapper).updateRatingSummary(101L, new BigDecimal("4.50"), 12);

        java.lang.reflect.Method upsert = CourseReviewService.class.getDeclaredMethod(
                "upsert", Long.class, Long.class, ReviewUpsertRequest.class);
        assertThat(upsert.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void upsertUpdatesExistingReviewAndRecalculatesSummary() {
        CourseEntity course = course(101L, 7L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(enrollmentMapper.selectCount(any())).thenReturn(1L);
        when(reviewMapper.upsert(any())).thenReturn(1);
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 20, 9, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 24, 11, 30);
        // 已存在评价：ON DUPLICATE 走更新分支，响应以重查行（更新后内容/时间）为准。
        when(reviewMapper.selectOne(any())).thenReturn(
                review(501L, 101L, 5001L, 3, "改分了", "VISIBLE", createdAt, updatedAt));
        when(reviewMapper.selectVisibleSummary(101L))
                .thenReturn(summary(new BigDecimal("3.00"), 1L));
        when(courseMapper.updateRatingSummary(101L, new BigDecimal("3.00"), 1)).thenReturn(1);

        CourseReviewResponse response = service().upsert(101L, 5001L, new ReviewUpsertRequest(3, "改分了"));

        assertThat(response.id()).isEqualTo("501");
        assertThat(response.rating()).isEqualTo(3);
        assertThat(response.content()).isEqualTo("改分了");
        assertThat(response.status()).isEqualTo("VISIBLE");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        verify(reviewMapper).upsert(any());
        verify(courseMapper).updateRatingSummary(101L, new BigDecimal("3.00"), 1);
    }

    // ---------------------------------------------------------------- hide：管理角色

    @Test
    void hideRejectsNonAdminRoleWith403() {
        assertThatThrownBy(() -> service().hide(501L, 30001L, Set.of("TEACHER", "STUDENT")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(403);
                });
        verify(reviewMapper, never()).selectById(anyLong());
        verify(courseMapper, never()).selectByIdForUpdate(anyLong());
    }

    @Test
    void hideMarksReviewHiddenAndRecalculatesSummary() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 20, 9, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 24, 10, 0);
        when(reviewMapper.selectById(501L)).thenReturn(
                review(501L, 101L, 5001L, 5, "好课", "VISIBLE", createdAt, updatedAt));
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course(101L, 7L));
        when(reviewMapper.selectVisibleSummary(101L))
                .thenReturn(summary(new BigDecimal("4.00"), 2L));
        when(courseMapper.updateRatingSummary(101L, new BigDecimal("4.00"), 2)).thenReturn(1);

        CourseReviewResponse response = service().hide(501L, 30001L, Set.of("SYSTEM_ADMIN"));

        assertThat(response.id()).isEqualTo("501");
        assertThat(response.status()).isEqualTo("HIDDEN");

        // P1b 竞态修复：窄更新只写 status/updated_by/updated_at（WHERE id=?），
        // 绝不整行回写 —— 陈旧实体（旧 rating/content）不会覆盖学生并发新提交。
        verify(reviewMapper, never()).updateById(any(CourseReviewEntity.class));
        verify(reviewMapper).updateStatus(eq(501L), eq("HIDDEN"), eq(30001L), any(LocalDateTime.class));

        verify(courseMapper).updateRatingSummary(101L, new BigDecimal("4.00"), 2);

        java.lang.reflect.Method hide = CourseReviewService.class.getDeclaredMethod(
                "hide", Long.class, Long.class, Set.class);
        assertThat(hide.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void hideAllowsSuperAdminRole() {
        when(reviewMapper.selectById(501L)).thenReturn(
                review(501L, 101L, 5001L, 5, "好课", "HIDDEN", LocalDateTime.of(2026, 8, 20, 9, 0),
                        LocalDateTime.of(2026, 8, 24, 10, 0)));

        CourseReviewResponse response = service().hide(501L, 30001L, Set.of("SUPER_ADMIN"));

        assertThat(response.status()).isEqualTo("HIDDEN");
    }

    @Test
    void hideAlreadyHiddenReviewIsIdempotent() {
        when(reviewMapper.selectById(501L)).thenReturn(
                review(501L, 101L, 5001L, 5, "好课", "HIDDEN", LocalDateTime.of(2026, 8, 20, 9, 0),
                        LocalDateTime.of(2026, 8, 24, 10, 0)));

        CourseReviewResponse response = service().hide(501L, 30001L, Set.of("SYSTEM_ADMIN"));

        assertThat(response.status()).isEqualTo("HIDDEN");
        verify(reviewMapper, never()).updateById(any(CourseReviewEntity.class));
        verify(courseMapper, never()).selectByIdForUpdate(anyLong());
        verify(courseMapper, never()).updateRatingSummary(anyLong(), any(), anyInt());
    }

    @Test
    void hideReturns404WhenReviewNotFound() {
        when(reviewMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service().hide(999L, 30001L, Set.of("SYSTEM_ADMIN")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.REVIEW_NOT_FOUND));
        verify(courseMapper, never()).selectByIdForUpdate(anyLong());
    }

    @Test
    void hideWithStaleEntityDoesNotOverwriteStudentRatingViaNarrowUpdate() {
        // P1b 并发竞态（管理端先读到旧行，学生随后改分）：
        // 管理端 selectById 拿到的是改分前实体（rating=5），学生已把库里改成 2 分。
        // 旧实现用陈旧实体整行 updateById 会把 5 分回写覆盖学生新提交；
        // 窄更新只写 status/updated_by/updated_at（WHERE id=?），rating/content
        // 根本不进入 UPDATE —— 库里评分保持学生最新值（端到端由 ReviewSummaryIT 锁定）。
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 20, 9, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 24, 10, 0);
        when(reviewMapper.selectById(501L)).thenReturn(
                review(501L, 101L, 5001L, 5, "旧内容", "VISIBLE", createdAt, updatedAt));
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course(101L, 7L));
        when(reviewMapper.selectVisibleSummary(101L))
                .thenReturn(summary(new BigDecimal("2.00"), 1L));
        when(courseMapper.updateRatingSummary(101L, new BigDecimal("2.00"), 1)).thenReturn(1);

        CourseReviewResponse response = service().hide(501L, 30001L, Set.of("SYSTEM_ADMIN"));

        assertThat(response.status()).isEqualTo("HIDDEN");
        // 写路径不含 rating/content：整行回写（updateById）被禁止，窄更新只带隐藏三字段。
        verify(reviewMapper, never()).updateById(any(CourseReviewEntity.class));
        verify(reviewMapper).updateStatus(eq(501L), eq("HIDDEN"), eq(30001L), any(LocalDateTime.class));
    }

    @Test
    void hideAfterStudentRerateKeepsNewRating() {
        // P1b 顺序模拟（评审要求）：先 upsert rating=5，再 upsert rating=2，
        // hide 后响应评分仍为 2 —— 学生最新提交不被隐藏动作覆盖。
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 20, 9, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 24, 11, 30);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course(101L, 7L));
        when(enrollmentMapper.selectCount(any())).thenReturn(1L);
        when(reviewMapper.upsert(any())).thenReturn(1);
        when(reviewMapper.selectOne(any())).thenReturn(
                review(501L, 101L, 5001L, 5, "第一次", "VISIBLE", createdAt, createdAt),
                review(501L, 101L, 5001L, 2, "改两星", "VISIBLE", createdAt, updatedAt));

        service().upsert(101L, 5001L, new ReviewUpsertRequest(5, "第一次"));
        service().upsert(101L, 5001L, new ReviewUpsertRequest(2, "改两星"));

        // 管理端隐藏读到学生改分后的最新行（rating=2）。
        when(reviewMapper.selectById(501L)).thenReturn(
                review(501L, 101L, 5001L, 2, "改两星", "VISIBLE", createdAt, updatedAt));
        when(reviewMapper.selectVisibleSummary(101L))
                .thenReturn(summary(new BigDecimal("2.00"), 1L));
        when(courseMapper.updateRatingSummary(101L, new BigDecimal("2.00"), 1)).thenReturn(1);

        CourseReviewResponse response = service().hide(501L, 30001L, Set.of("SYSTEM_ADMIN"));

        assertThat(response.rating()).isEqualTo(2);
        assertThat(response.content()).isEqualTo("改两星");
        assertThat(response.status()).isEqualTo("HIDDEN");
        verify(reviewMapper, never()).updateById(any(CourseReviewEntity.class));
        verify(reviewMapper).updateStatus(eq(501L), eq("HIDDEN"), eq(30001L), any(LocalDateTime.class));
    }

    // ---------------------------------------------------------------- helpers

    private static CourseEntity course(Long id, Long version) {
        CourseEntity entity = new CourseEntity();
        entity.setId(id);
        entity.setOwnerTeacherId(2001L);
        entity.setLifecycleStatus("PUBLISHED");
        entity.setVersion(version);
        return entity;
    }

    private static CourseReviewEntity review(Long id, Long courseId, Long studentId, int rating,
            String content, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        CourseReviewEntity entity = new CourseReviewEntity();
        entity.setId(id);
        entity.setCourseId(courseId);
        entity.setStudentId(studentId);
        entity.setRating(rating);
        entity.setContent(content);
        entity.setStatus(status);
        entity.setCreatedBy(studentId);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedBy(studentId);
        entity.setUpdatedAt(updatedAt);
        return entity;
    }

    private static CourseReviewSummaryRow summary(BigDecimal ratingAvg, Long ratingCount) {
        CourseReviewSummaryRow row = new CourseReviewSummaryRow();
        row.setRatingAvg(ratingAvg);
        row.setRatingCount(ratingCount);
        return row;
    }
}
