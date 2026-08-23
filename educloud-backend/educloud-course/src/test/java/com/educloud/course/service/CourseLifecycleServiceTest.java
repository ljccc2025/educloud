package com.educloud.course.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.AuditEventMapper;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseTeacherMapper;
import com.educloud.course.mapper.CourseVersionMapper;
import com.educloud.course.messaging.CourseEventPublisher;
import com.educloud.course.observability.AuditWriter;
import com.educloud.course.support.MybatisPlusTestSupport;
import com.educloud.course.support.TeacherAccessGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M05 任务 10：课程生命周期（下架/重上架/归档）单元测试。
 *
 * <p>依据：规格 §6/§9 与任务 10 ——
 * <ul>
 *   <li>offline：仅 PUBLISHED → OFFLINE，其余生命周期 → 409 COURSE_STATE_CONFLICT；</li>
 *   <li>republish：仅 OFFLINE 且有 published_version_id → PUBLISHED（M05 就绪 gate 恒放行，
 *       course_content_readiness_projection 不参与判断）；OFFLINE 以外（含 ARCHIVED）→ 409，
 *       无 published_version_id → 409；</li>
 *   <li>archive：仅 OFFLINE → ARCHIVED；PUBLISHED 直接 archive → 409（必须先下架）；</li>
 *   <li>归属校验（§9）：下架/归档/重上架均须 course_teacher 归属，跨教师 → 403；</li>
 *   <li>锁根（selectByIdForUpdate）+ 根乐观锁冲突 → 409 VERSION_CONFLICT；</li>
 *   <li>outbox：CourseOfflined/CourseRepublished/CourseArchived 由 CourseEventPublisher
 *       同事务落库（任务 15 补 dispatcher），事件参数用 Mockito verify 断言。</li>
 * </ul></p>
 */
@ExtendWith(MockitoExtension.class)
class CourseLifecycleServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisPlusTestSupport.registerTableInfo(CourseEntity.class, CourseTeacherEntity.class);
    }

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private CourseTeacherMapper courseTeacherMapper;

    @Mock
    private CourseVersionMapper courseVersionMapper;

    @Mock
    private CourseEventPublisher eventPublisher;

    @Mock
    private FileClient fileClient;

    @Mock
    private AuditEventMapper auditEventMapper;

    @Mock
    private RequestContextAccessor requestContextAccessor;

    // ---------------------------------------------------------------- offline

    @Test
    void offlineMovesPublishedCourseToOfflineAndPublishesEvent() throws Exception {
        CourseEntity course = course(101L, 1001L, 301L, "PUBLISHED", 7L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        bumpVersionOnRootUpdate();

        service().offline(101L, 1001L);

        verify(courseTeacherMapper).selectCount(any());
        ArgumentCaptor<CourseEntity> captor = ArgumentCaptor.forClass(CourseEntity.class);
        verify(courseMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLifecycleStatus()).isEqualTo("OFFLINE");
        assertThat(captor.getValue().getPublishedVersionId()).isEqualTo(301L);
        // 下架只切根生命周期，不改版本状态；事件带根更新后的乐观锁版本。
        verify(eventPublisher).courseOfflined(eq(101L), eq(301L), eq(8L), any(LocalDateTime.class));

        java.lang.reflect.Method offline = CourseService.class.getDeclaredMethod(
                "offline", Long.class, Long.class);
        assertThat(offline.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void offlineRejectsNonPublishedWith409() {
        CourseEntity course = course(101L, 1001L, 301L, "DRAFT", 3L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().offline(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_STATE_CONFLICT);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(courseMapper, never()).updateById(any(CourseEntity.class));
        verify(eventPublisher, never()).courseOfflined(any(), any(), anyLong(), any());
    }

    @Test
    void offlineRejectsCrossTeacherWith403() {
        CourseEntity course = course(101L, 2002L, 301L, "PUBLISHED", 3L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service().offline(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(403);
                });
        verify(courseMapper, never()).updateById(any(CourseEntity.class));
        verify(eventPublisher, never()).courseOfflined(any(), any(), anyLong(), any());
    }

    @Test
    void offlineReturns404WhenCourseNotFound() {
        when(courseMapper.selectByIdForUpdate(999L)).thenReturn(null);

        assertThatThrownBy(() -> service().offline(999L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FOUND));
        verify(courseTeacherMapper, never()).selectCount(any());
    }

    // ---------------------------------------------------------------- republish

    @Test
    void republishMovesOfflineCourseToPublishedAndPublishesEvent() throws Exception {
        CourseEntity course = course(101L, 1001L, 301L, "OFFLINE", 5L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        bumpVersionOnRootUpdate();

        service().republish(101L, 1001L);

        ArgumentCaptor<CourseEntity> captor = ArgumentCaptor.forClass(CourseEntity.class);
        verify(courseMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLifecycleStatus()).isEqualTo("PUBLISHED");
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
        verify(eventPublisher).courseRepublished(eq(101L), eq(301L), eq(6L), any(LocalDateTime.class));

        java.lang.reflect.Method republish = CourseService.class.getDeclaredMethod(
                "republish", Long.class, Long.class);
        assertThat(republish.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void republishRejectsNonOfflineWith409() {
        CourseEntity course = course(101L, 1001L, 301L, "PUBLISHED", 2L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().republish(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_STATE_CONFLICT);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(courseMapper, never()).updateById(any(CourseEntity.class));
        verify(eventPublisher, never()).courseRepublished(any(), any(), anyLong(), any());
    }

    @Test
    void republishRejectsArchivedWith409() {
        // 归档后不可重上架：ARCHIVED 不是 OFFLINE → 409。
        CourseEntity course = course(101L, 1001L, 301L, "ARCHIVED", 2L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().republish(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_STATE_CONFLICT);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(courseMapper, never()).updateById(any(CourseEntity.class));
    }

    @Test
    void republishRejectsMissingPublishedVersionWith409() {
        // OFFLINE 但无有效发布版本（published_version_id 为空）→ 不可重上架。
        CourseEntity course = course(101L, 1001L, null, "OFFLINE", 2L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().republish(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_STATE_CONFLICT);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(courseMapper, never()).updateById(any(CourseEntity.class));
        verify(eventPublisher, never()).courseRepublished(any(), any(), anyLong(), any());
    }

    @Test
    void republishRejectsCrossTeacherWith403() {
        CourseEntity course = course(101L, 2002L, 301L, "OFFLINE", 2L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service().republish(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED));
        verify(courseMapper, never()).updateById(any(CourseEntity.class));
    }

    @Test
    void republishMapsRootOptimisticLockMissTo409() {
        CourseEntity course = course(101L, 1001L, 301L, "OFFLINE", 5L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        when(courseMapper.updateById(any(CourseEntity.class))).thenReturn(0);

        assertThatThrownBy(() -> service().republish(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.VERSION_CONFLICT));
        verify(eventPublisher, never()).courseRepublished(any(), any(), anyLong(), any());
    }

    // ---------------------------------------------------------------- archive

    @Test
    void archiveMovesOfflineCourseToArchivedAndPublishesEvent() throws Exception {
        CourseEntity course = course(101L, 1001L, 301L, "OFFLINE", 5L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        bumpVersionOnRootUpdate();

        service().archive(101L, 1001L);

        ArgumentCaptor<CourseEntity> captor = ArgumentCaptor.forClass(CourseEntity.class);
        verify(courseMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLifecycleStatus()).isEqualTo("ARCHIVED");
        verify(eventPublisher).courseArchived(eq(101L), eq(301L), eq(6L), any(LocalDateTime.class));

        java.lang.reflect.Method archive = CourseService.class.getDeclaredMethod(
                "archive", Long.class, Long.class);
        assertThat(archive.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void archiveRejectsPublishedDirectlyWith409() {
        // 规格 §6：已发布课程必须先下架，PUBLISHED 直接 archive → 409。
        CourseEntity course = course(101L, 1001L, 301L, "PUBLISHED", 2L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().archive(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_STATE_CONFLICT);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(courseMapper, never()).updateById(any(CourseEntity.class));
        verify(eventPublisher, never()).courseArchived(any(), any(), anyLong(), any());
    }

    @Test
    void archiveRejectsNonOfflineWith409() {
        CourseEntity course = course(101L, 1001L, null, "DRAFT", 2L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().archive(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_STATE_CONFLICT));
        verify(courseMapper, never()).updateById(any(CourseEntity.class));
    }

    @Test
    void archiveRejectsCrossTeacherWith403() {
        CourseEntity course = course(101L, 2002L, 301L, "OFFLINE", 2L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service().archive(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(403);
                });
        verify(courseMapper, never()).updateById(any(CourseEntity.class));
    }

    // ---------------------------------------------------------------- helpers

    private CourseService service() {
        return new CourseService(
                courseMapper,
                courseTeacherMapper,
                courseVersionMapper,
                new TeacherAccessGuard(courseTeacherMapper),
                eventPublisher,
                fileClient,
                new AuditWriter(auditEventMapper, requestContextAccessor,
                        new ObjectMapper(), Clock.systemUTC()));
    }

    private void bumpVersionOnRootUpdate() {
        doAnswer(invocation -> {
            CourseEntity entity = invocation.getArgument(0);
            entity.setVersion(entity.getVersion() + 1);
            return 1;
        }).when(courseMapper).updateById(any(CourseEntity.class));
    }

    private static CourseEntity course(
            Long id, Long ownerTeacherId, Long publishedVersionId, String lifecycle, Long version) {
        CourseEntity entity = new CourseEntity();
        entity.setId(id);
        entity.setOwnerTeacherId(ownerTeacherId);
        entity.setPublishedVersionId(publishedVersionId);
        entity.setLifecycleStatus(lifecycle);
        entity.setVersion(version);
        return entity;
    }
}
