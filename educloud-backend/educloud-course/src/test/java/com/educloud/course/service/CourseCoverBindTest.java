package com.educloud.course.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.educloud.common.error.BusinessException;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M05 任务 12：封面 bind 集成 —— 上传者属主校验委托 File（bind 携带 uploaderUserId=教师），
 * coverFileId 变化才 bind/unbind；File 拒绝（他人 fileId → 403 COURSE_ACCESS_DENIED）
 * 时草稿保存失败且不落库。测试以 mock FileClient 模拟 File 侧语义（File 侧上传者校验
 * 由 FileBindingServiceTest 新增用例覆盖）。
 */
@ExtendWith(MockitoExtension.class)
class CourseCoverBindTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
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

    // ---------- PUT /course-drafts/{versionId}：封面 bind/unbind ----------

    @Test
    void updateDraftBindsNewCoverAndUnbindsOldWithTeacherAsUploader() {
        CourseEntity course = course(101L, 1001L, 301L);
        CourseVersionEntity version = version(301L, 101L, 1, "DRAFT", "旧标题", 77L);
        when(courseVersionMapper.selectById(301L)).thenReturn(version);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        when(courseVersionMapper.update(any(), any())).thenReturn(1);
        when(courseTeacherMapper.selectList(any())).thenReturn(List.of());

        CourseDraftResponse response = versionService().updateDraft(301L, 1001L,
                updateRequest("新标题", "88"));

        assertThat(response.coverFileId()).isEqualTo("88");
        // bind 请求体断言（而非仅 verify 调用）：courseId/fileId/uploaderUserId 三项
        // 必须与本次保存一致 —— uploaderUserId=当前教师（File 侧上传者属主校验依赖它）。
        ArgumentCaptor<Long> courseIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> fileIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> uploaderCaptor = ArgumentCaptor.forClass(Long.class);
        verify(fileClient).bindCover(courseIdCaptor.capture(), fileIdCaptor.capture(), uploaderCaptor.capture());
        assertThat(courseIdCaptor.getValue()).isEqualTo(101L);
        assertThat(fileIdCaptor.getValue()).isEqualTo(88L);
        assertThat(uploaderCaptor.getValue()).isEqualTo(1001L);
        verify(fileClient).unbindCover(101L, 77L);

        // UPDATE 实体内容断言：新封面 fileId=88 必须真实写入 course_version 的
        // cover_file_id 字段（bind 与落库同源，防止只调 File 不写库的假绿）。
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaUpdateWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(courseVersionMapper).update(isNull(), wrapperCaptor.capture());
        LambdaUpdateWrapper<CourseVersionEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSet()).contains("cover_file_id=");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(88L);
    }

    @Test
    void updateDraftUnbindsOldCoverWhenCleared() {
        CourseEntity course = course(101L, 1001L, 301L);
        CourseVersionEntity version = version(301L, 101L, 1, "DRAFT", "旧标题", 77L);
        when(courseVersionMapper.selectById(301L)).thenReturn(version);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        when(courseVersionMapper.update(any(), any())).thenReturn(1);
        when(courseTeacherMapper.selectList(any())).thenReturn(List.of());

        CourseDraftResponse response = versionService().updateDraft(301L, 1001L,
                updateRequest("新标题", null));

        assertThat(response.coverFileId()).isNull();
        verify(fileClient, never()).bindCover(any(), any(), any());
        verify(fileClient).unbindCover(101L, 77L);
    }

    @Test
    void updateDraftSkipsFileCallsWhenCoverUnchanged() {
        CourseEntity course = course(101L, 1001L, 301L);
        CourseVersionEntity version = version(301L, 101L, 1, "DRAFT", "旧标题", 77L);
        when(courseVersionMapper.selectById(301L)).thenReturn(version);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        when(courseVersionMapper.update(any(), any())).thenReturn(1);
        when(courseTeacherMapper.selectList(any())).thenReturn(List.of());

        versionService().updateDraft(301L, 1001L, updateRequest("新标题", "77"));

        // 封面未变化时不应触发写侧 bind/unbind；响应回显的只读 grant（coverUrl）允许调用。
        verify(fileClient, never()).bindCover(any(), any(), any());
        verify(fileClient, never()).unbindCover(any(), any());
    }

    @Test
    void updateDraftPropagatesFileBindRejectionWithoutWriting() {
        // 伪造他人 fileId：File 侧 uploaderUserId 校验失败 → 403 COURSE_ACCESS_DENIED，
        // Course 保存草稿必须失败且不落 UPDATE（不落脏状态）。
        CourseEntity course = course(101L, 1001L, 301L);
        CourseVersionEntity version = version(301L, 101L, 1, "DRAFT", "旧标题", 77L);
        when(courseVersionMapper.selectById(301L)).thenReturn(version);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        doThrow(new BusinessException(CourseErrorCode.COURSE_ACCESS_DENIED,
                "File 拒绝访问: bind 9001 (HTTP 403)"))
                .when(fileClient).bindCover(101L, 88L, 1001L);

        assertThatThrownBy(() -> versionService().updateDraft(301L, 1001L,
                updateRequest("新标题", "88")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED));

        verify(courseVersionMapper, never()).update(any(), any());
        verify(fileClient, never()).unbindCover(any(), any());
    }

    // ---------- POST /courses：建课携带封面 ----------

    @Test
    void createCourseBindsCoverWithTeacherAsUploader() {
        CourseCreateRequest request = new CourseCreateRequest(
                "Java 入门", null, null, "77", "BEGINNER",
                new BigDecimal("0.00"), "CNY", "5");
        assignIdOnCourseInsert(101L);
        assignIdOnTeacherInsert(201L);
        assignIdOnVersionInsert(301L);
        when(courseMapper.updateById(any(CourseEntity.class))).thenReturn(1);

        CourseDraftResponse response = courseService().createCourse(1001L, request, Set.of("TEACHER"));

        org.assertj.core.api.Assertions.assertThat(response.coverFileId()).isEqualTo("77");
        verify(fileClient).bindCover(101L, 77L, 1001L);
    }

    @Test
    void createCourseWithoutCoverNeverCallsFile() {
        CourseCreateRequest request = new CourseCreateRequest(
                "Java 入门", null, null, null, "BEGINNER",
                new BigDecimal("0.00"), "CNY", "5");
        assignIdOnCourseInsert(101L);
        assignIdOnTeacherInsert(201L);
        assignIdOnVersionInsert(301L);
        when(courseMapper.updateById(any(CourseEntity.class))).thenReturn(1);

        courseService().createCourse(1001L, request, Set.of("TEACHER"));

        verifyNoInteractions(fileClient);
    }

    @Test
    void createCoursePropagatesFileBindRejectionWithRollbackOfInserts() {
        CourseCreateRequest request = new CourseCreateRequest(
                "Java 入门", null, null, "77", "BEGINNER",
                new BigDecimal("0.00"), "CNY", "5");
        assignIdOnCourseInsert(101L);
        assignIdOnTeacherInsert(201L);
        assignIdOnVersionInsert(301L);
        doThrow(new BusinessException(CourseErrorCode.COURSE_ACCESS_DENIED,
                "File 拒绝访问: bind 77 (HTTP 403)"))
                .when(fileClient).bindCover(101L, 77L, 1001L);

        assertThatThrownBy(() -> courseService().createCourse(1001L, request, Set.of("TEACHER")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED));
        // bind 失败 → 事务回滚：course 根指针更新不得发生（insert 由 @Transactional 回滚）。
        verify(courseMapper, never()).updateById(any(CourseEntity.class));
    }

    // ---------- helpers ----------

    private CourseService courseService() {
        return new CourseService(
                courseMapper, courseTeacherMapper, courseVersionMapper,
                new TeacherAccessGuard(courseTeacherMapper), eventPublisher, fileClient,
                auditWriter);
    }

    private CourseVersionService versionService() {
        return new CourseVersionService(courseMapper, courseVersionMapper, courseTeacherMapper,
                new TeacherAccessGuard(courseTeacherMapper), fileClient, eventPublisher);
    }

    private static CourseDraftUpdateRequest updateRequest(String title, String coverFileId) {
        return new CourseDraftUpdateRequest(
                title, "副标题", "描述", coverFileId, "BEGINNER",
                new BigDecimal("10.00"), "CNY", "2");
    }

    private static CourseEntity course(Long id, Long ownerTeacherId, Long draftVersionId) {
        CourseEntity entity = new CourseEntity();
        entity.setId(id);
        entity.setOwnerTeacherId(ownerTeacherId);
        entity.setLifecycleStatus("DRAFT");
        entity.setDraftVersionId(draftVersionId);
        entity.setVersion(0L);
        return entity;
    }

    private static CourseVersionEntity version(
            Long id, Long courseId, int versionNo, String status, String title, Long coverFileId) {
        CourseVersionEntity entity = new CourseVersionEntity();
        entity.setId(id);
        entity.setCourseId(courseId);
        entity.setVersionNo(versionNo);
        entity.setVersionStatus(status);
        entity.setTitle(title);
        entity.setSubtitle("副标题");
        entity.setDescription("描述");
        entity.setCoverFileId(coverFileId);
        entity.setLevel("BEGINNER");
        entity.setPrice(new BigDecimal("10.00"));
        entity.setCurrency("CNY");
        entity.setCategoryId(2L);
        return entity;
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
}
