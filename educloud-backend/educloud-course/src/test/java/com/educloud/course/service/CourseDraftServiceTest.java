package com.educloud.course.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.course.dto.request.CourseCreateRequest;
import com.educloud.course.dto.request.CourseDraftUpdateRequest;
import com.educloud.course.dto.response.CourseDraftResponse;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.entity.CourseVersionEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.AuditEventMapper;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseTeacherMapper;
import com.educloud.course.mapper.CourseVersionMapper;
import com.educloud.course.messaging.CourseEventPublisher;
import com.educloud.course.observability.AuditWriter;
import com.educloud.course.support.MybatisPlusTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Clock;
import java.util.Set;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M05 任务 8：课程创建与草稿管理服务单元测试。
 *
 * <p>依据：任务 8 步骤 1 —— POST /courses 建根+首版 DRAFT（同一事务三表插入）；
 * PUT 只允许 DRAFT（VERSION_NOT_DRAFT）；跨教师访问草稿 403；POST drafts 从
 * PUBLISHED/REJECTED 复制新草稿并 version_no+1。Mockito 直接 mock 三个 Mapper
 * （Mock 的 insert 用 doAnswer 模拟 MyBatis-Plus ASSIGN_ID 回写实体 id）。
 * 归属校验走真实 {@link TeacherAccessGuard}（依赖 mock 的 course_teacher Mapper）。</p>
 */
@ExtendWith(MockitoExtension.class)
class CourseDraftServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // 纯 Mockito 单测没有 MyBatis 运行期 Mapper 注册，LambdaWrapper 渲染列名依赖
        // TableInfo 缓存；共享支持类注册（与真实运行期行为一致）。
        MybatisPlusTestSupport.registerTableInfo(
                CourseEntity.class, CourseVersionEntity.class, CourseTeacherEntity.class);
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
    private AuditEventMapper auditEventMapper;

    @Mock
    private RequestContextAccessor requestContextAccessor;

    @Mock
    private AuditWriter auditWriter;

    @Mock
    private FileClient fileClient;

    @Test
    void createCourseInsertsRootOwnerAndFirstDraftInOneTransaction() throws Exception {
        CourseCreateRequest request = new CourseCreateRequest(
                "Java 入门", "从零开始的 Java", "涵盖基础语法与面向对象",
                "77", "BEGINNER", new BigDecimal("199.00"), "CNY", "5");
        assignIdOnCourseInsert(101L);
        assignIdOnTeacherInsert(201L);
        assignIdOnVersionInsert(301L);
        when(courseMapper.updateById(any(CourseEntity.class))).thenReturn(1);

        CourseDraftResponse response = courseService().createCourse(1001L, request, Set.of("TEACHER"));

        assertThat(response.courseId()).isEqualTo("101");
        assertThat(response.versionId()).isEqualTo("301");
        assertThat(response.versionNo()).isEqualTo(1);
        assertThat(response.versionStatus()).isEqualTo("DRAFT");
        assertThat(response.lifecycleStatus()).isEqualTo("DRAFT");
        assertThat(response.title()).isEqualTo("Java 入门");
        assertThat(response.subtitle()).isEqualTo("从零开始的 Java");
        assertThat(response.description()).isEqualTo("涵盖基础语法与面向对象");
        assertThat(response.coverFileId()).isEqualTo("77");
        assertThat(response.level()).isEqualTo("BEGINNER");
        assertThat(response.price()).isEqualTo("199.00");
        assertThat(response.currency()).isEqualTo("CNY");
        assertThat(response.categoryId()).isEqualTo("5");
        assertThat(response.teachers()).containsExactly(new CourseDraftResponse.Teacher("1001", "OWNER"));
        verify(auditWriter).write("COURSE_CREATED", "course", "101",
                1001L, Set.of("TEACHER"), "SUCCESS", null);

        ArgumentCaptor<CourseEntity> courseCaptor = ArgumentCaptor.forClass(CourseEntity.class);
        verify(courseMapper).insert(courseCaptor.capture());
        CourseEntity course = courseCaptor.getValue();
        assertThat(course.getOwnerTeacherId()).isEqualTo(1001L);
        assertThat(course.getLifecycleStatus()).isEqualTo("DRAFT");
        assertThat(course.getDraftVersionId()).isEqualTo(301L);
        assertThat(course.getCreatedBy()).isEqualTo(1001L);

        ArgumentCaptor<CourseTeacherEntity> teacherCaptor = ArgumentCaptor.forClass(CourseTeacherEntity.class);
        verify(courseTeacherMapper).insert(teacherCaptor.capture());
        CourseTeacherEntity teacher = teacherCaptor.getValue();
        assertThat(teacher.getCourseId()).isEqualTo(101L);
        assertThat(teacher.getTeacherId()).isEqualTo(1001L);
        assertThat(teacher.getTeacherRole()).isEqualTo("OWNER");

        ArgumentCaptor<CourseVersionEntity> versionCaptor = ArgumentCaptor.forClass(CourseVersionEntity.class);
        verify(courseVersionMapper).insert(versionCaptor.capture());
        CourseVersionEntity version = versionCaptor.getValue();
        assertThat(version.getCourseId()).isEqualTo(101L);
        assertThat(version.getVersionNo()).isEqualTo(1);
        assertThat(version.getVersionStatus()).isEqualTo("DRAFT");
        assertThat(version.getCategoryId()).isEqualTo(5L);
        assertThat(version.getTitle()).isEqualTo("Java 入门");
        assertThat(version.getCoverFileId()).isEqualTo(77L);
        assertThat(version.getLevel()).isEqualTo("BEGINNER");
        assertThat(version.getPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
        assertThat(version.getCurrency()).isEqualTo("CNY");
        assertThat(version.getCreatedBy()).isEqualTo(1001L);

        // 建课必须是同一事务：course + course_teacher + course_version 一起提交。
        java.lang.reflect.Method createMethod = CourseService.class.getDeclaredMethod(
                "createCourse", Long.class, CourseCreateRequest.class, Set.class);
        assertThat(createMethod.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void createCourseRejectsSnowflakeIdBeyondLongRangeWith400() {
        // 19 位但 > Long.MAX_VALUE：@Pattern("\d{1,19}") 可通过，service 层 Long.parseLong
        // 兜底 → 400 VALIDATION_FAILED，不落版本。
        CourseCreateRequest request = new CourseCreateRequest(
                "Java 入门", null, null, null, "BEGINNER",
                new BigDecimal("1.00"), "CNY", "9999999999999999999");
        assignIdOnCourseInsert(101L);
        assignIdOnTeacherInsert(201L);

        assertThatThrownBy(() -> courseService().createCourse(1001L, request, Set.of("TEACHER")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(400);
                });
        verify(courseVersionMapper, never()).insert(any(CourseVersionEntity.class));
    }

    @Test
    void currentDraftReturnsDraftVersionForOwnerTeacher() {
        CourseEntity course = course(101L, 1001L, 301L, 5L);
        CourseVersionEntity draft = version(301L, 101L, 1, "DRAFT", "草稿标题");
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        when(courseVersionMapper.selectById(301L)).thenReturn(draft);
        when(courseTeacherMapper.selectList(any())).thenReturn(
                List.of(teacherRow(201L, 101L, 1001L, "OWNER")));

        CourseDraftResponse response = versionService().getCurrentDraft(101L, 1001L);

        assertThat(response.courseId()).isEqualTo("101");
        assertThat(response.versionId()).isEqualTo("301");
        assertThat(response.versionStatus()).isEqualTo("DRAFT");
        assertThat(response.title()).isEqualTo("草稿标题");
        assertThat(response.teachers()).containsExactly(new CourseDraftResponse.Teacher("1001", "OWNER"));
    }

    @Test
    void currentDraftReturns404WhenCourseHasNoDraftVersion() {
        CourseEntity course = course(101L, 1001L, null, 5L);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> versionService().getCurrentDraft(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FOUND));
        verify(courseVersionMapper, never()).selectById(any());
    }

    @Test
    void currentDraftRejectsCrossTeacherAccessWith403() {
        CourseEntity course = course(101L, 2002L, 301L, 5L);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> versionService().getCurrentDraft(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(403);
                });
        verify(courseVersionMapper, never()).selectById(any());
    }

    @Test
    void updateDraftUpdatesAllFieldsWithDraftStatusCondition() {
        CourseEntity course = course(101L, 1001L, 301L, 5L);
        CourseVersionEntity version = version(301L, 101L, 1, "DRAFT", "旧标题");
        when(courseVersionMapper.selectById(301L)).thenReturn(version);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        when(courseVersionMapper.update(any(), any())).thenReturn(1);
        when(courseTeacherMapper.selectList(any())).thenReturn(
                List.of(teacherRow(201L, 101L, 1001L, "OWNER")));

        CourseDraftResponse response = versionService().updateDraft(301L, 1001L,
                new CourseDraftUpdateRequest(
                        "新标题", "新副标题", "新描述", "88", "INTERMEDIATE",
                        new BigDecimal("299.00"), "USD", "8"));

        assertThat(response.title()).isEqualTo("新标题");
        assertThat(response.versionId()).isEqualTo("301");
        assertThat(response.price()).isEqualTo("299.00");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaUpdateWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(courseVersionMapper).update(isNull(), wrapperCaptor.capture());
        LambdaUpdateWrapper<CourseVersionEntity> wrapper = wrapperCaptor.getValue();
        // 全量字段更新。
        assertThat(wrapper.getSqlSet())
                .contains("title=", "subtitle=", "description=", "cover_file_id=",
                        "level=", "price=", "currency=", "category_id=");
        // 并发防护：UPDATE ... WHERE id=? AND version_status='DRAFT'。
        assertThat(wrapper.getSqlSegment())
                .contains("id =")
                .contains("version_status =");
        assertThat(wrapper.getParamNameValuePairs())
                .containsValues("新标题", "新副标题", "新描述", 88L, "INTERMEDIATE",
                        new BigDecimal("299.00"), "USD", 8L);
    }

    @Test
    void updateDraftRejectsNonDraftVersion() {
        CourseEntity course = course(101L, 1001L, 301L, 5L);
        CourseVersionEntity version = version(301L, 101L, 1, "PENDING_REVIEW", "待审标题");
        when(courseVersionMapper.selectById(301L)).thenReturn(version);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> versionService().updateDraft(301L, 1001L, updateRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.VERSION_NOT_DRAFT);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
        verify(courseVersionMapper, never()).update(any(), any());
    }

    @Test
    void updateDraftRejectsWhenConditionalUpdateAffectsZeroRows() {
        CourseEntity course = course(101L, 1001L, 301L, 5L);
        CourseVersionEntity version = version(301L, 101L, 1, "DRAFT", "草稿标题");
        when(courseVersionMapper.selectById(301L)).thenReturn(version);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        // 并发下状态已被改为非 DRAFT：条件更新影响行数 0 → VERSION_NOT_DRAFT。
        when(courseVersionMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> versionService().updateDraft(301L, 1001L, updateRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.VERSION_NOT_DRAFT));
    }

    @Test
    void updateDraftRejectsCrossTeacherAccessWith403() {
        CourseEntity course = course(101L, 2002L, 301L, 5L);
        CourseVersionEntity version = version(301L, 101L, 1, "DRAFT", "他人草稿");
        when(courseVersionMapper.selectById(301L)).thenReturn(version);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> versionService().updateDraft(301L, 1001L, updateRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED));
        verify(courseVersionMapper, never()).update(any(), any());
    }

    @Test
    void updateDraftReturns404WhenVersionNotFound() {
        when(courseVersionMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> versionService().updateDraft(999L, 1001L, updateRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FOUND));
    }

    @Test
    void createDraftCopiesPublishedVersionWithIncrementedVersionNo() {
        CourseEntity course = course(101L, 1001L, null, 5L);
        CourseVersionEntity source = version(301L, 101L, 1, "PUBLISHED", "已发布标题");
        source.setSubtitle("发布副标题");
        source.setDescription("发布描述");
        source.setCoverFileId(77L);
        source.setLevel("INTERMEDIATE");
        source.setPrice(new BigDecimal("99.00"));
        source.setCurrency("CNY");
        source.setCategoryId(5L);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        when(courseVersionMapper.selectOne(any())).thenReturn(source);
        assignIdOnVersionInsert(302L);
        when(courseMapper.updateById(any(CourseEntity.class))).thenReturn(1);
        when(courseTeacherMapper.selectList(any())).thenReturn(
                List.of(teacherRow(201L, 101L, 1001L, "OWNER")));

        CourseDraftResponse response = versionService().createDraftFromPublishedOrRejected(101L, 1001L);

        assertThat(response.versionId()).isEqualTo("302");
        assertThat(response.versionNo()).isEqualTo(2);
        assertThat(response.versionStatus()).isEqualTo("DRAFT");
        assertThat(response.title()).isEqualTo("已发布标题");
        assertThat(response.coverFileId()).isEqualTo("77");
        assertThat(response.price()).isEqualTo("99.00");

        ArgumentCaptor<CourseVersionEntity> captor = ArgumentCaptor.forClass(CourseVersionEntity.class);
        verify(courseVersionMapper).insert(captor.capture());
        CourseVersionEntity inserted = captor.getValue();
        assertThat(inserted.getCourseId()).isEqualTo(101L);
        assertThat(inserted.getVersionNo()).isEqualTo(2);
        assertThat(inserted.getVersionStatus()).isEqualTo("DRAFT");
        assertThat(inserted.getCategoryId()).isEqualTo(5L);
        assertThat(inserted.getTitle()).isEqualTo("已发布标题");
        assertThat(inserted.getSubtitle()).isEqualTo("发布副标题");
        assertThat(inserted.getDescription()).isEqualTo("发布描述");
        assertThat(inserted.getCoverFileId()).isEqualTo(77L);
        assertThat(inserted.getLevel()).isEqualTo("INTERMEDIATE");
        assertThat(inserted.getPrice()).isEqualByComparingTo(new BigDecimal("99.00"));
        assertThat(inserted.getCurrency()).isEqualTo("CNY");
        assertThat(inserted.getCreatedBy()).isEqualTo(1001L);

        ArgumentCaptor<CourseEntity> courseCaptor = ArgumentCaptor.forClass(CourseEntity.class);
        verify(courseMapper).updateById(courseCaptor.capture());
        assertThat(courseCaptor.getValue().getDraftVersionId()).isEqualTo(302L);
    }

    @Test
    void createDraftCopiesRejectedVersionWithIncrementedVersionNo() {
        CourseEntity course = course(101L, 1001L, 301L, 5L);
        CourseVersionEntity rejected = version(301L, 101L, 3, "REJECTED", "被驳回标题");
        rejected.setCoverFileId(66L);
        rejected.setPrice(new BigDecimal("50.00"));
        rejected.setCategoryId(6L);
        rejected.setLevel("ADVANCED");
        rejected.setCurrency("CNY");
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        when(courseVersionMapper.selectById(301L)).thenReturn(rejected);
        when(courseVersionMapper.selectOne(any())).thenReturn(rejected);
        assignIdOnVersionInsert(304L);
        when(courseMapper.updateById(any(CourseEntity.class))).thenReturn(1);
        when(courseTeacherMapper.selectList(any())).thenReturn(List.of());

        CourseDraftResponse response = versionService().createDraftFromPublishedOrRejected(101L, 1001L);

        assertThat(response.versionId()).isEqualTo("304");
        assertThat(response.versionNo()).isEqualTo(4);
        assertThat(response.versionStatus()).isEqualTo("DRAFT");
        assertThat(response.title()).isEqualTo("被驳回标题");

        ArgumentCaptor<CourseVersionEntity> captor = ArgumentCaptor.forClass(CourseVersionEntity.class);
        verify(courseVersionMapper).insert(captor.capture());
        assertThat(captor.getValue().getVersionNo()).isEqualTo(4);
    }

    @Test
    void createDraftReturnsExistingDraftWhenAlreadyDraft() {
        CourseEntity course = course(101L, 1001L, 301L, 5L);
        CourseVersionEntity draft = version(301L, 101L, 2, "DRAFT", "现有草稿");
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        when(courseVersionMapper.selectById(301L)).thenReturn(draft);
        when(courseTeacherMapper.selectList(any())).thenReturn(List.of());

        CourseDraftResponse response = versionService().createDraftFromPublishedOrRejected(101L, 1001L);

        assertThat(response.versionId()).isEqualTo("301");
        assertThat(response.versionNo()).isEqualTo(2);
        verify(courseVersionMapper, never()).insert(any(CourseVersionEntity.class));
        verify(courseMapper, never()).updateById(any(CourseEntity.class));
    }

    @Test
    void createDraftRejectsWhenCurrentVersionIsPendingReview() {
        CourseEntity course = course(101L, 1001L, 301L, 5L);
        CourseVersionEntity pending = version(301L, 101L, 1, "PENDING_REVIEW", "待审");
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        when(courseVersionMapper.selectById(301L)).thenReturn(pending);

        assertThatThrownBy(() -> versionService().createDraftFromPublishedOrRejected(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.VERSION_NOT_DRAFT));
        verify(courseVersionMapper, never()).insert(any(CourseVersionEntity.class));
    }

    @Test
    void createDraftRejectsCrossTeacherAccessWith403() {
        CourseEntity course = course(101L, 2002L, 301L, 5L);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> versionService().createDraftFromPublishedOrRejected(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(403);
                });
        verify(courseVersionMapper, never()).selectById(any());
        verify(courseVersionMapper, never()).insert(any(CourseVersionEntity.class));
    }

    @Test
    void createDraftReturns404WhenNoPublishedOrRejectedSource() {
        CourseEntity course = course(101L, 1001L, null, 5L);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        when(courseVersionMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> versionService().createDraftFromPublishedOrRejected(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FOUND));
    }

    @Test
    void createDraftMapsConcurrentDuplicateVersionToVersionNotDraft() {
        CourseEntity course = course(101L, 1001L, null, 5L);
        CourseVersionEntity source = version(301L, 101L, 1, "PUBLISHED", "已发布标题");
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        when(courseVersionMapper.selectOne(any())).thenReturn(source);
        // 并发复制撞 uk_course_version_no → DuplicateKeyException 必须映射为 409 而非 500。
        doThrow(new DuplicateKeyException("uk_course_version_no"))
                .when(courseVersionMapper).insert(any(CourseVersionEntity.class));

        assertThatThrownBy(() -> versionService().createDraftFromPublishedOrRejected(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.VERSION_NOT_DRAFT);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void createDraftTreatsZeroAffectedRootUpdateAsConflict() {
        CourseEntity course = course(101L, 1001L, null, 5L);
        CourseVersionEntity source = version(301L, 101L, 1, "PUBLISHED", "已发布标题");
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        when(courseVersionMapper.selectOne(any())).thenReturn(source);
        assignIdOnVersionInsert(302L);
        // course 根乐观锁未命中（并发改根）→ 409 VERSION_CONFLICT。
        when(courseMapper.updateById(any(CourseEntity.class))).thenReturn(0);

        assertThatThrownBy(() -> versionService().createDraftFromPublishedOrRejected(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.VERSION_CONFLICT));
    }

    @Test
    void updateDraftRejectsVersionNotPointedByCourseDraftPointer() {
        // 状态机下不可达的防御：DRAFT 版本不是 course.draft_version_id 指向的当前草稿 → 409。
        CourseEntity course = course(101L, 1001L, 399L, 5L);
        CourseVersionEntity version = version(301L, 101L, 1, "DRAFT", "孤儿草稿");
        when(courseVersionMapper.selectById(301L)).thenReturn(version);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> versionService().updateDraft(301L, 1001L, updateRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.VERSION_NOT_DRAFT));
        verify(courseVersionMapper, never()).update(any(), any());
    }

    private CourseService courseService() {
        return new CourseService(
                courseMapper,
                courseTeacherMapper,
                courseVersionMapper,
                new TeacherAccessGuard(courseTeacherMapper),
                eventPublisher,
                fileClient,
                auditWriter);
    }

    private CourseVersionService versionService() {
        return new CourseVersionService(courseMapper, courseVersionMapper, courseTeacherMapper,
                new TeacherAccessGuard(courseTeacherMapper), fileClient);
    }

    private static CourseDraftUpdateRequest updateRequest() {
        return new CourseDraftUpdateRequest(
                "标题", "副标题", "描述", "1", "BEGINNER", new BigDecimal("10.00"), "CNY", "2");
    }

    private void assignIdOnCourseInsert(Long id) {
        doAnswer(invocation -> {
            CourseEntity entity = invocation.getArgument(0);
            entity.setId(id);
            return 1;
        }).when(courseMapper).insert(any(CourseEntity.class));
    }

    private void assignIdOnTeacherInsert(Long id) {
        doAnswer(invocation -> {
            CourseTeacherEntity entity = invocation.getArgument(0);
            entity.setId(id);
            return 1;
        }).when(courseTeacherMapper).insert(any(CourseTeacherEntity.class));
    }

    private void assignIdOnVersionInsert(Long id) {
        doAnswer(invocation -> {
            CourseVersionEntity entity = invocation.getArgument(0);
            entity.setId(id);
            return 1;
        }).when(courseVersionMapper).insert(any(CourseVersionEntity.class));
    }

    private static CourseEntity course(Long id, Long ownerTeacherId, Long draftVersionId, Long version) {
        CourseEntity entity = new CourseEntity();
        entity.setId(id);
        entity.setOwnerTeacherId(ownerTeacherId);
        entity.setLifecycleStatus("DRAFT");
        entity.setDraftVersionId(draftVersionId);
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
        return entity;
    }

    private static CourseTeacherEntity teacherRow(Long id, Long courseId, Long teacherId, String role) {
        CourseTeacherEntity entity = new CourseTeacherEntity();
        entity.setId(id);
        entity.setCourseId(courseId);
        entity.setTeacherId(teacherId);
        entity.setTeacherRole(role);
        return entity;
    }
}
