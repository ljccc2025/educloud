package com.educloud.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.PageResponse;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.course.dto.response.CourseStudentResponse;
import com.educloud.course.dto.response.EnrollmentResponse;
import com.educloud.course.dto.response.MyCourseResponse;
import com.educloud.course.entity.CourseEnrollmentEntity;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.entity.CourseVersionEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.AuditEventMapper;
import com.educloud.course.mapper.CourseEnrollmentMapper;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseMyCourseRow;
import com.educloud.course.mapper.CourseStudentRow;
import com.educloud.course.mapper.CourseTeacherMapper;
import com.educloud.course.mapper.CourseVersionMapper;
import com.educloud.course.messaging.CourseEventPublisher;
import com.educloud.course.observability.AuditWriter;
import com.educloud.course.observability.CourseMetrics;
import com.educloud.course.support.MybatisPlusTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.educloud.course.support.TeacherAccessGuard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M05 任务 13：免费选课/我的课程 服务单元测试。
 *
 * <p>依据：规格 §6/§7 与任务 13 ——
 * <ul>
 *   <li>选课：锁根（selectByIdForUpdate）→ 仅 PUBLISHED（OFFLINE/ARCHIVED → 409
 *       COURSE_OFFLINE_OR_ARCHIVED）→ 取 published version 校验免费（付费 → 409
 *       COURSE_NOT_FREE）→ 已存在 enrollment 幂等返回现状（不重复计数/不发事件）→
 *       插入 ACTIVE/FREE + course.enrollment_count 乐观锁递增 + outbox EnrollmentCreated；</li>
 *   <li>并发兜底：uk(course_id,student_id) 冲突（DuplicateKeyException）→ 重查返回现状；</li>
 *   <li>我的课程：enrollment JOIN course JOIN published version 分页，封面按页一次
 *       File 批量 grant（subject=USER 学生本人，已选课学生可看自己的已发布课程封面）。</li>
 * </ul></p>
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisPlusTestSupport.registerTableInfo(
                CourseEntity.class,
                CourseVersionEntity.class,
                CourseEnrollmentEntity.class,
                CourseTeacherEntity.class);
    }

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private CourseVersionMapper versionMapper;

    @Mock
    private CourseEnrollmentMapper enrollmentMapper;

    @Mock
    private CourseTeacherMapper courseTeacherMapper;

    @Mock
    private CourseEventPublisher eventPublisher;

    @Mock
    private FileClient fileClient;

    @Mock
    private AuditEventMapper auditEventMapper;

    @Mock
    private RequestContextAccessor requestContextAccessor;

    @Mock
    private CourseMetrics courseMetrics;

    @Mock
    private AuditWriter auditWriter;

    // ---------------------------------------------------------------- 选课：成功/幂等

    @Test
    void enrollInsertsActiveFreeEnrollmentIncrementsCountAndPublishesEvent() throws Exception {
        CourseEntity course = course(101L, 1001L, 301L, "PUBLISHED", 7L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(versionMapper.selectById(301L)).thenReturn(version(301L, 101L, "0.00"));
        when(enrollmentMapper.selectOne(any())).thenReturn(null);
        assignEnrollmentId(501L);
        when(courseMapper.incrementEnrollmentCount(101L, 7L)).thenReturn(1);

        EnrollmentResponse response = service().enroll(101L, 5001L, Set.of("STUDENT"));

        assertThat(response.enrollmentId()).isEqualTo("501");
        assertThat(response.courseId()).isEqualTo("101");
        assertThat(response.studentId()).isEqualTo("5001");
        assertThat(response.source()).isEqualTo("FREE");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.enrolledAt()).isNotNull();

        ArgumentCaptor<CourseEnrollmentEntity> captor = ArgumentCaptor.forClass(CourseEnrollmentEntity.class);
        verify(enrollmentMapper).insert(captor.capture());
        CourseEnrollmentEntity inserted = captor.getValue();
        assertThat(inserted.getCourseId()).isEqualTo(101L);
        assertThat(inserted.getStudentId()).isEqualTo(5001L);
        assertThat(inserted.getSource()).isEqualTo("FREE");
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getEnrolledAt()).isNotNull();
        assertThat(inserted.getVersion()).isZero();

        verify(courseMapper).incrementEnrollmentCount(101L, 7L);
        verify(eventPublisher).enrollmentCreated(
                eq(501L), eq(101L), eq(5001L), eq("FREE"), eq(1001L), isNull(), eq(0L), any(LocalDateTime.class));
        verify(courseMetrics).recordEnrollmentCreated();
        verify(auditWriter).write("ENROLLMENT_CREATED", "enrollment", "501",
                5001L, Set.of("STUDENT"), "SUCCESS", null);

        java.lang.reflect.Method enroll = EnrollmentService.class.getDeclaredMethod(
                "enroll", Long.class, Long.class, Set.class);
        assertThat(enroll.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void enrollDuplicateActiveReturnsCurrentStateWithoutSideEffects() {
        CourseEntity course = course(101L, 1001L, 301L, "PUBLISHED", 7L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(versionMapper.selectById(301L)).thenReturn(version(301L, 101L, "0.00"));
        LocalDateTime enrolledAt = LocalDateTime.of(2026, 8, 23, 10, 30);
        when(enrollmentMapper.selectOne(any())).thenReturn(enrollment(501L, 101L, 5001L, enrolledAt));

        EnrollmentResponse response = service().enroll(101L, 5001L, Set.of("STUDENT"));

        assertThat(response.enrollmentId()).isEqualTo("501");
        assertThat(response.enrolledAt()).isEqualTo(enrolledAt);
        verify(enrollmentMapper, never()).insert(any(CourseEnrollmentEntity.class));
        verify(courseMapper, never()).incrementEnrollmentCount(anyLong(), anyLong());
        verify(eventPublisher, never()).enrollmentCreated(any(), any(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void enrollMapsConcurrentDuplicateKeyToIdempotentReturn() {
        // uk(course_id, student_id) 兜底：并发下 insert 撞唯一键 → 捕获后重查返回现状
        // （不重复计数、不发事件；锁根已在入口序列化同一课程选课，此为最后防线）。
        // 审查修复：重查必须用当前读（SELECT ... FOR UPDATE）——MySQL REPEATABLE READ
        // 下本事务一致读快照看不到并发事务已提交的 uk 行，普通 selectOne 会返回 null
        // 导致 500（预检查 selectOne 仍可一致读：锁根后同课程并发已被序列化）。
        CourseEntity course = course(101L, 1001L, 301L, "PUBLISHED", 7L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(versionMapper.selectById(301L)).thenReturn(version(301L, 101L, "0.00"));
        CourseEnrollmentEntity existing = enrollment(501L, 101L, 5001L, LocalDateTime.of(2026, 8, 23, 9, 0));
        when(enrollmentMapper.selectOne(any())).thenReturn(null);
        when(enrollmentMapper.selectByCourseAndStudentForUpdate(101L, 5001L)).thenReturn(existing);
        doThrow(new DuplicateKeyException("uk_course_enrollment")).when(enrollmentMapper)
                .insert(any(CourseEnrollmentEntity.class));

        EnrollmentResponse response = service().enroll(101L, 5001L, Set.of("STUDENT"));

        assertThat(response.enrollmentId()).isEqualTo("501");
        assertThat(response.enrolledAt()).isEqualTo(LocalDateTime.of(2026, 8, 23, 9, 0));
        // 兜底重查走当前读方法（不是一致读 selectOne）。
        verify(enrollmentMapper).selectByCourseAndStudentForUpdate(101L, 5001L);
        verify(courseMapper, never()).incrementEnrollmentCount(anyLong(), anyLong());
        verify(eventPublisher, never()).enrollmentCreated(any(), any(), any(), any(), any(), any(), anyLong(), any());
    }

    // ---------------------------------------------------------------- 选课：拒绝路径

    @Test
    void enrollPaidCourseRejectsWith409() {
        CourseEntity course = course(101L, 1001L, 301L, "PUBLISHED", 7L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(versionMapper.selectById(301L)).thenReturn(version(301L, 101L, "199.00"));

        assertThatThrownBy(() -> service().enroll(101L, 5001L, Set.of("STUDENT")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FREE);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(enrollmentMapper, never()).insert(any(CourseEnrollmentEntity.class));
        verify(courseMapper, never()).incrementEnrollmentCount(anyLong(), anyLong());
        verify(eventPublisher, never()).enrollmentCreated(any(), any(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void enrollOfflineCourseRejectsWith409() {
        CourseEntity course = course(101L, 1001L, 301L, "OFFLINE", 7L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);

        assertThatThrownBy(() -> service().enroll(101L, 5001L, Set.of("STUDENT")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_OFFLINE_OR_ARCHIVED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(enrollmentMapper, never()).insert(any(CourseEnrollmentEntity.class));
    }

    @Test
    void enrollArchivedCourseRejectsWith409() {
        CourseEntity course = course(101L, 1001L, 301L, "ARCHIVED", 7L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);

        assertThatThrownBy(() -> service().enroll(101L, 5001L, Set.of("STUDENT")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_OFFLINE_OR_ARCHIVED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(enrollmentMapper, never()).insert(any(CourseEnrollmentEntity.class));
    }

    @Test
    void enrollReturns404WhenCourseNotFound() {
        when(courseMapper.selectByIdForUpdate(999L)).thenReturn(null);

        assertThatThrownBy(() -> service().enroll(999L, 5001L, Set.of("STUDENT")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FOUND));
        verify(versionMapper, never()).selectById(any());
    }

    @Test
    void enrollMapsCountIncrementMissTo409() {
        CourseEntity course = course(101L, 1001L, 301L, "PUBLISHED", 7L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(versionMapper.selectById(301L)).thenReturn(version(301L, 101L, "0.00"));
        when(enrollmentMapper.selectOne(any())).thenReturn(null);
        assignEnrollmentId(501L);
        when(courseMapper.incrementEnrollmentCount(101L, 7L)).thenReturn(0);

        assertThatThrownBy(() -> service().enroll(101L, 5001L, Set.of("STUDENT")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.VERSION_CONFLICT);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(eventPublisher, never()).enrollmentCreated(any(), any(), any(), any(), any(), any(), anyLong(), any());
    }

    // ---------------------------------------------------------------- 我的课程

    @Test
    void myCoursesReturnsPageAndGrantsCoversOncePerPage() {
        Page<CourseMyCourseRow> page = new Page<>(1, 20);
        page.setRecords(List.of(
                myCourseRow(501L, 101L, "高等数学", 88L, LocalDateTime.of(2026, 8, 23, 9, 0)),
                myCourseRow(502L, 102L, "Java 入门", null, LocalDateTime.of(2026, 8, 23, 8, 0))));
        page.setTotal(2);
        when(enrollmentMapper.selectMyCoursesPage(any(Page.class), eq(5001L))).thenReturn(page);
        when(fileClient.grantCatalogUrls(Map.of(88L, 101L), 5001L))
                .thenReturn(Map.of(88L, "http://bucket/cover-88"));

        PageResponse<MyCourseResponse> response = service().myCourses(5001L, 1, 20);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(2);
        assertThat(response.items()).hasSize(2);
        MyCourseResponse first = response.items().get(0);
        assertThat(first.courseId()).isEqualTo("101");
        assertThat(first.title()).isEqualTo("高等数学");
        assertThat(first.coverUrl()).isEqualTo("http://bucket/cover-88");
        assertThat(first.status()).isEqualTo("ACTIVE");
        assertThat(first.enrolledAt()).isEqualTo(LocalDateTime.of(2026, 8, 23, 9, 0));
        assertThat(response.items().get(1).coverUrl()).isNull();
        // 每页至多一次批量 grant（USER subject=当前学生）。
        verify(fileClient).grantCatalogUrls(Map.of(88L, 101L), 5001L);
    }

    @Test
    void myCoursesSkipsGrantWhenPageHasNoCovers() {
        Page<CourseMyCourseRow> page = new Page<>(1, 20);
        page.setRecords(List.of(myCourseRow(501L, 101L, "无封面课", null, LocalDateTime.of(2026, 8, 23, 9, 0))));
        page.setTotal(1);
        when(enrollmentMapper.selectMyCoursesPage(any(Page.class), eq(5001L))).thenReturn(page);

        PageResponse<MyCourseResponse> response = service().myCourses(5001L, 1, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).coverUrl()).isNull();
        verifyNoInteractions(fileClient);
    }

    @Test
    void myCoursesClampsPagination() {
        Page<CourseMyCourseRow> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(enrollmentMapper.selectMyCoursesPage(any(Page.class), eq(5001L))).thenReturn(page);

        PageResponse<MyCourseResponse> response = service().myCourses(5001L, -3, 500);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(100);
        assertThat(response.total()).isZero();
    }


    // ---------------------------------------------------------------- 边界：发布版本/价格

    @Test
    void enrollRejectsMissingPublishedVersionPointerWith409() {
        // PUBLISHED 但 published_version_id 为空（数据不一致防御）→ 选课目标不可用 409。
        CourseEntity course = course(101L, 1001L, null, "PUBLISHED", 7L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);

        assertThatThrownBy(() -> service().enroll(101L, 5001L, Set.of("STUDENT")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_OFFLINE_OR_ARCHIVED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(versionMapper, never()).selectById(any());
        verify(enrollmentMapper, never()).insert(any(CourseEnrollmentEntity.class));
    }

    @Test
    void enrollRejectsMissingPublishedVersionRowWith409() {
        // published_version_id 指向的行不存在（数据不一致防御）→ 选课目标不可用 409。
        CourseEntity course = course(101L, 1001L, 301L, "PUBLISHED", 7L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        when(versionMapper.selectById(301L)).thenReturn(null);

        assertThatThrownBy(() -> service().enroll(101L, 5001L, Set.of("STUDENT")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_OFFLINE_OR_ARCHIVED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(enrollmentMapper, never()).insert(any(CourseEnrollmentEntity.class));
    }

    @Test
    void enrollRejectsNullPriceWith409() {
        // price 为空视为不可免费选课（DB 列 NOT NULL DEFAULT 0，防御数据异常）。
        CourseEntity course = course(101L, 1001L, 301L, "PUBLISHED", 7L);
        when(courseMapper.selectByIdForUpdate(101L)).thenReturn(course);
        CourseVersionEntity version = version(301L, 101L, "0.00");
        version.setPrice(null);
        when(versionMapper.selectById(301L)).thenReturn(version);

        assertThatThrownBy(() -> service().enroll(101L, 5001L, Set.of("STUDENT")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FREE);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(enrollmentMapper, never()).insert(any(CourseEnrollmentEntity.class));
    }

    // ---------------------------------------------------------------- 学生列表：分页钳制

    @Test
    void listStudentsClampsPagination() {
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        Page<CourseStudentRow> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(enrollmentMapper.selectStudentPage(any(Page.class), eq(101L))).thenReturn(page);

        PageResponse<CourseStudentResponse> response = service().listStudents(101L, 1001L, -3, 500);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(100);
        assertThat(response.total()).isZero();
    }

    // ---------------------------------------------------------------- helpers

    private EnrollmentService service() {
        return new EnrollmentService(
                courseMapper,
                versionMapper,
                enrollmentMapper,
                new TeacherAccessGuard(courseTeacherMapper),
                eventPublisher,
                fileClient,
                courseMetrics,
                auditWriter);
    }

    private void assignEnrollmentId(Long id) {
        doAnswer(invocation -> {
            CourseEnrollmentEntity entity = invocation.getArgument(0);
            entity.setId(id);
            return 1;
        }).when(enrollmentMapper).insert(any(CourseEnrollmentEntity.class));
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

    private static CourseVersionEntity version(Long id, Long courseId, String price) {
        CourseVersionEntity entity = new CourseVersionEntity();
        entity.setId(id);
        entity.setCourseId(courseId);
        entity.setPrice(new BigDecimal(price));
        return entity;
    }

    private static CourseEnrollmentEntity enrollment(Long id, Long courseId, Long studentId, LocalDateTime enrolledAt) {
        CourseEnrollmentEntity entity = new CourseEnrollmentEntity();
        entity.setId(id);
        entity.setCourseId(courseId);
        entity.setStudentId(studentId);
        entity.setSource("FREE");
        entity.setStatus("ACTIVE");
        entity.setEnrolledAt(enrolledAt);
        entity.setVersion(0L);
        return entity;
    }

    private static CourseMyCourseRow myCourseRow(
            Long enrollmentId, Long courseId, String title, Long coverFileId, LocalDateTime enrolledAt) {
        CourseMyCourseRow row = new CourseMyCourseRow();
        row.setEnrollmentId(enrollmentId);
        row.setCourseId(courseId);
        row.setTitle(title);
        row.setCoverFileId(coverFileId);
        row.setStatus("ACTIVE");
        row.setEnrolledAt(enrolledAt);
        return row;
    }
}
