package com.educloud.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.PageResponse;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.course.dto.request.CourseListQuery;
import com.educloud.course.dto.response.CourseDetailResponse;
import com.educloud.course.dto.response.CourseSummaryResponse;
import com.educloud.course.entity.CourseCategoryEntity;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseEnrollmentEntity;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.entity.CourseVersionEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.CourseCatalogRow;
import com.educloud.course.mapper.CourseCategoryMapper;
import com.educloud.course.mapper.CourseEnrollmentMapper;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseTeacherMapper;
import com.educloud.course.mapper.CourseVersionMapper;
import com.educloud.course.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M05 任务 11：课程公开列表与详情服务单元测试。
 *
 * <p>依据：任务 11 步骤 1 —— 过滤（keyword/categoryId/level/priceRange 转发 SQL）、
 * 排序白名单（非法 sort 400，缺省 popular）、分页参数（默认 1/20、size 上限 100）、
 * enrolled 标记（登录批量 IN、匿名 false）、详情可见性（PUBLISHED 公开、DRAFT/
 * PENDING_REVIEW/OFFLINE 仅归属教师、他人/匿名/ARCHIVED 404）。SQL JOIN 过滤与
 * ORDER BY 映射在 CourseMapper.selectCatalogPage 注解 SQL（白名单值在服务层校验后
 * 原样转发），本测试验证服务层转发与映射。</p>
 */
@ExtendWith(MockitoExtension.class)
class CourseCatalogServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // LambdaWrapper 渲染列名依赖 TableInfo 缓存（共享支持类，与真实运行期一致）。
        MybatisPlusTestSupport.registerTableInfo(
                CourseEntity.class,
                CourseVersionEntity.class,
                CourseEnrollmentEntity.class,
                CourseTeacherEntity.class,
                CourseCategoryEntity.class);
    }

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private CourseVersionMapper versionMapper;

    @Mock
    private CourseEnrollmentMapper enrollmentMapper;

    @Mock
    private CourseCategoryMapper categoryMapper;

    @Mock
    private CourseTeacherMapper teacherMapper;

    private CourseCatalogService service() {
        return new CourseCatalogService(
                courseMapper, versionMapper, enrollmentMapper, categoryMapper, teacherMapper);
    }

    // ---------- 列表：过滤/排序/分页 ----------

    @Test
    void listForwardsFiltersAndSortToMapperAndMapsRows() {
        Page<CourseCatalogRow> result = new Page<>(1, 20);
        result.setRecords(List.of(row(101L, "高等数学", new BigDecimal("199.00"), 1001L, "数学", 88)));
        result.setTotal(1);
        when(courseMapper.selectCatalogPage(any(), any(), any(), any(), any(), any())).thenReturn(result);

        CourseListQuery query =
                new CourseListQuery("数学", "5", "BEGINNER", "under200", "price-asc", 1, 20);
        PageResponse<CourseSummaryResponse> response = service().list(query, null);

        assertThat(response.items()).hasSize(1);
        CourseSummaryResponse item = response.items().get(0);
        assertThat(item.id()).isEqualTo("101");
        assertThat(item.title()).isEqualTo("高等数学");
        assertThat(item.teacherName()).isEqualTo("1001");
        assertThat(item.categoryName()).isEqualTo("数学");
        assertThat(item.level()).isEqualTo("BEGINNER");
        assertThat(item.price()).isEqualTo("199.00");
        assertThat(item.ratingAvg()).isEqualByComparingTo("4.50");
        assertThat(item.ratingCount()).isEqualTo(12);
        assertThat(item.enrollmentCount()).isEqualTo(88);
        assertThat(item.enrolled()).isFalse();
        assertThat(item.coverUrl()).isNull();

        verify(courseMapper).selectCatalogPage(
                any(Page.class), eq("数学"), eq(5L), eq("BEGINNER"), eq("under200"), eq("price-asc"));
    }

    @Test
    void listAcceptsAllWhitelistedSorts() {
        for (String sort : List.of("popular", "newest", "price-asc", "price-desc", "rating")) {
            Page<CourseCatalogRow> result = new Page<>(1, 20);
            result.setRecords(List.of());
            result.setTotal(0);
            when(courseMapper.selectCatalogPage(any(), any(), any(), any(), any(), any()))
                    .thenReturn(result);
            service().list(new CourseListQuery(null, null, null, null, sort, 1, 20), null);
            verify(courseMapper).selectCatalogPage(
                    any(Page.class), isNull(), isNull(), isNull(), isNull(), eq(sort));
        }
    }

    @Test
    void listDefaultsToPopularSortWhenSortMissing() {
        Page<CourseCatalogRow> result = new Page<>(1, 20);
        result.setRecords(List.of());
        result.setTotal(0);
        when(courseMapper.selectCatalogPage(any(), any(), any(), any(), any(), any())).thenReturn(result);

        service().list(new CourseListQuery(null, null, null, null, null, null, null), null);

        verify(courseMapper).selectCatalogPage(
                any(Page.class), isNull(), isNull(), isNull(), isNull(), eq("popular"));
    }

    @Test
    void listRejectsSortOutsideWhitelistWith400() {
        assertThatThrownBy(() -> service().list(
                new CourseListQuery(null, null, null, null, "bogus", 1, 20), null))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(400);
                });
        verify(courseMapper, never()).selectCatalogPage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void listRejectsPriceRangeOutsideEnumWith400() {
        assertThatThrownBy(() -> service().list(
                new CourseListQuery(null, null, null, "cheap", null, 1, 20), null))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(400);
                });
        verify(courseMapper, never()).selectCatalogPage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void listDefaultsPaginationToPageOneSizeTwenty() {
        Page<CourseCatalogRow> result = new Page<>(1, 20);
        result.setRecords(List.of());
        result.setTotal(0);
        when(courseMapper.selectCatalogPage(any(), any(), any(), any(), any(), any())).thenReturn(result);

        PageResponse<CourseSummaryResponse> response =
                service().list(new CourseListQuery(null, null, null, null, null, null, null), null);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.total()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void listCapsSizeAt100AndKeepsPage() {
        when(courseMapper.selectCatalogPage(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Page<CourseCatalogRow> page = invocation.getArgument(0);
                    page.setRecords(List.of(row(102L, "t2", new BigDecimal("0.00"), 1L, "c", 0)));
                    page.setTotal(150);
                    return page;
                });

        PageResponse<CourseSummaryResponse> response = service().list(
                new CourseListQuery(null, null, null, null, null, 2, 500), null);

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.pageSize()).isEqualTo(100);
        assertThat(response.total()).isEqualTo(150);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    // ---------- 列表：enrolled 标记 ----------

    @Test
    void listMarksEnrolledCoursesForLoggedInUserInOneBatchQuery() {
        Page<CourseCatalogRow> result = new Page<>(1, 20);
        result.setRecords(List.of(
                row(101L, "A", new BigDecimal("0.00"), 1L, "c", 1),
                row(102L, "B", new BigDecimal("0.00"), 1L, "c", 2)));
        result.setTotal(2);
        when(courseMapper.selectCatalogPage(any(), any(), any(), any(), any(), any())).thenReturn(result);

        CourseEnrollmentEntity enrollment = new CourseEnrollmentEntity();
        enrollment.setCourseId(101L);
        enrollment.setStudentId(5001L);
        enrollment.setStatus("ACTIVE");
        when(enrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));

        PageResponse<CourseSummaryResponse> response =
                service().list(new CourseListQuery(null, null, null, null, null, 1, 20), 5001L);

        assertThat(response.items().get(0).enrolled()).isTrue();
        assertThat(response.items().get(1).enrolled()).isFalse();

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(enrollmentMapper).selectList(captor.capture());
        // LambdaWrapper 的参数值在渲染 SQL 段（getSqlSegment）时写入 paramNameValuePairs，
        // 断言前先渲染，与 CourseDraftServiceTest 的 wrapper 断言同一方式。
        LambdaQueryWrapper<?> wrapper = captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("course_id").contains("student_id").contains("status");
        Map<String, Object> params = wrapper.getParamNameValuePairs();
        // MyBatis-Plus 渲染时把 IN 集合展开为独立参数值（此处为 101L/102L 两项）。
        assertThat(params).containsValues(5001L, "ACTIVE", 101L, 102L);
    }

    @Test
    void anonymousListNeverQueriesEnrollments() {
        Page<CourseCatalogRow> result = new Page<>(1, 20);
        result.setRecords(List.of(row(101L, "A", new BigDecimal("0.00"), 1L, "c", 1)));
        result.setTotal(1);
        when(courseMapper.selectCatalogPage(any(), any(), any(), any(), any(), any())).thenReturn(result);

        PageResponse<CourseSummaryResponse> response =
                service().list(new CourseListQuery(null, null, null, null, null, 1, 20), null);

        assertThat(response.items().get(0).enrolled()).isFalse();
        verify(enrollmentMapper, never()).selectList(any());
    }

    // ---------- 详情：可见性 ----------

    @Test
    void detailReturnsPublishedCourseToAnyone() {
        CourseEntity course = course(101L, 1001L, "PUBLISHED", 301L, null);
        when(courseMapper.selectById(101L)).thenReturn(course);
        CourseVersionEntity version = version(301L, 101L, "PUBLISHED", "高等数学精讲", new BigDecimal("199.00"));
        when(versionMapper.selectById(301L)).thenReturn(version);
        when(categoryMapper.selectById(5L)).thenReturn(category(5L, "数学"));
        when(teacherMapper.selectList(any())).thenReturn(List.of(
                teacher(1L, 101L, 1001L, "OWNER"),
                teacher(2L, 101L, 2002L, "CO_TEACHER")));
        when(enrollmentMapper.selectCount(any())).thenReturn(1L);

        CourseDetailResponse dto = service().detail(101L, 5001L);

        assertThat(dto.id()).isEqualTo("101");
        assertThat(dto.title()).isEqualTo("高等数学精讲");
        assertThat(dto.subtitle()).isEqualTo("副标题");
        assertThat(dto.description()).isEqualTo("描述");
        assertThat(dto.price()).isEqualTo("199.00");
        assertThat(dto.currency()).isEqualTo("CNY");
        assertThat(dto.categoryId()).isEqualTo("5");
        assertThat(dto.categoryName()).isEqualTo("数学");
        assertThat(dto.level()).isEqualTo("BEGINNER");
        assertThat(dto.teachers()).containsExactly(
                new CourseDetailResponse.Teacher("1001", "OWNER"),
                new CourseDetailResponse.Teacher("2002", "CO_TEACHER"));
        assertThat(dto.ratingAvg()).isEqualByComparingTo("4.50");
        assertThat(dto.ratingCount()).isEqualTo(12);
        assertThat(dto.enrollmentCount()).isEqualTo(345);
        assertThat(dto.enrolled()).isTrue();
        assertThat(dto.lifecycleStatus()).isEqualTo("PUBLISHED");
        assertThat(dto.reviews()).isEmpty();
        assertThat(dto.coverUrl()).isNull();
    }

    @Test
    void detailAllowsOwnerTeacherToSeeDraft() {
        CourseEntity course = course(101L, 1001L, "DRAFT", null, 301L);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(teacherMapper.selectCount(any())).thenReturn(1L);
        when(versionMapper.selectById(301L)).thenReturn(
                version(301L, 101L, "DRAFT", "草稿标题", new BigDecimal("0.00")));
        when(categoryMapper.selectById(5L)).thenReturn(category(5L, "数学"));
        when(teacherMapper.selectList(any())).thenReturn(List.of(teacher(1L, 101L, 1001L, "OWNER")));

        CourseDetailResponse dto = service().detail(101L, 1001L);

        assertThat(dto.lifecycleStatus()).isEqualTo("DRAFT");
        assertThat(dto.title()).isEqualTo("草稿标题");
        assertThat(dto.enrolled()).isFalse();
    }

    @Test
    void detailAllowsOwnerTeacherToSeePendingReviewCourse() {
        CourseEntity course = course(101L, 1001L, "PENDING_REVIEW", null, 301L);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(teacherMapper.selectCount(any())).thenReturn(1L);
        when(versionMapper.selectById(301L)).thenReturn(
                version(301L, 101L, "PENDING_REVIEW", "待审", new BigDecimal("299.00")));
        when(categoryMapper.selectById(5L)).thenReturn(category(5L, "数学"));
        when(teacherMapper.selectList(any())).thenReturn(List.of(teacher(1L, 101L, 1001L, "OWNER")));

        CourseDetailResponse dto = service().detail(101L, 1001L);

        assertThat(dto.lifecycleStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(dto.title()).isEqualTo("待审");
    }

    @Test
    void detailAllowsOwnerTeacherToSeeOfflineCourse() {
        CourseEntity course = course(101L, 1001L, "OFFLINE", 301L, null);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(teacherMapper.selectCount(any())).thenReturn(1L);
        when(versionMapper.selectById(301L)).thenReturn(
                version(301L, 101L, "PUBLISHED", "下架课", new BigDecimal("99.00")));
        when(categoryMapper.selectById(5L)).thenReturn(category(5L, "数学"));
        when(teacherMapper.selectList(any())).thenReturn(List.of(teacher(1L, 101L, 1001L, "OWNER")));

        CourseDetailResponse dto = service().detail(101L, 1001L);

        assertThat(dto.lifecycleStatus()).isEqualTo("OFFLINE");
        assertThat(dto.title()).isEqualTo("下架课");
    }

    @Test
    void detailReturns404ForAnonymousOnDraft() {
        CourseEntity course = course(101L, 1001L, "DRAFT", null, 301L);
        when(courseMapper.selectById(101L)).thenReturn(course);

        assertThatThrownBy(() -> service().detail(101L, null))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FOUND);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(404);
                });
        verify(teacherMapper, never()).selectCount(any());
    }

    @Test
    void detailReturns404ForNonOwnerTeacherOnDraft() {
        CourseEntity course = course(101L, 1001L, "DRAFT", null, 301L);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(teacherMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service().detail(101L, 2002L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FOUND));
        verify(versionMapper, never()).selectById(any());
    }

    @Test
    void detailReturns404ForArchivedEvenForOwner() {
        CourseEntity course = course(101L, 1001L, "ARCHIVED", 301L, null);
        when(courseMapper.selectById(101L)).thenReturn(course);

        assertThatThrownBy(() -> service().detail(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FOUND));
        verify(teacherMapper, never()).selectCount(any());
    }

    @Test
    void detailReturns404WhenCourseDoesNotExist() {
        when(courseMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service().detail(999L, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FOUND));
    }

    @Test
    void detailReturns404WhenReadableVersionMissing() {
        CourseEntity course = course(101L, 1001L, "PUBLISHED", null, null);
        when(courseMapper.selectById(101L)).thenReturn(course);
        when(versionMapper.selectById(null)).thenReturn(null);

        assertThatThrownBy(() -> service().detail(101L, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FOUND));
    }

    // ---------- helpers ----------

    private static CourseCatalogRow row(Long courseId, String title, BigDecimal price,
            Long teacherId, String categoryName, int enrollmentCount) {
        CourseCatalogRow row = new CourseCatalogRow();
        row.setCourseId(courseId);
        row.setTitle(title);
        row.setPrice(price);
        row.setCurrency("CNY");
        row.setTeacherId(teacherId);
        row.setCategoryName(categoryName);
        row.setCategoryId(5L);
        row.setLevel("BEGINNER");
        row.setRatingAvg(new BigDecimal("4.50"));
        row.setRatingCount(12);
        row.setEnrollmentCount(enrollmentCount);
        return row;
    }

    private static CourseEntity course(Long id, Long ownerTeacherId, String lifecycleStatus,
            Long publishedVersionId, Long draftVersionId) {
        CourseEntity entity = new CourseEntity();
        entity.setId(id);
        entity.setOwnerTeacherId(ownerTeacherId);
        entity.setLifecycleStatus(lifecycleStatus);
        entity.setPublishedVersionId(publishedVersionId);
        entity.setDraftVersionId(draftVersionId);
        entity.setRatingAvg(new BigDecimal("4.50"));
        entity.setRatingCount(12);
        entity.setEnrollmentCount(345);
        return entity;
    }

    private static CourseVersionEntity version(Long id, Long courseId, String status,
            String title, BigDecimal price) {
        CourseVersionEntity entity = new CourseVersionEntity();
        entity.setId(id);
        entity.setCourseId(courseId);
        entity.setVersionStatus(status);
        entity.setTitle(title);
        entity.setSubtitle("副标题");
        entity.setDescription("描述");
        entity.setPrice(price);
        entity.setCurrency("CNY");
        entity.setLevel("BEGINNER");
        entity.setCategoryId(5L);
        return entity;
    }

    private static CourseCategoryEntity category(Long id, String name) {
        CourseCategoryEntity entity = new CourseCategoryEntity();
        entity.setId(id);
        entity.setName(name);
        return entity;
    }

    private static CourseTeacherEntity teacher(Long id, Long courseId, Long teacherId, String role) {
        CourseTeacherEntity entity = new CourseTeacherEntity();
        entity.setId(id);
        entity.setCourseId(courseId);
        entity.setTeacherId(teacherId);
        entity.setTeacherRole(role);
        return entity;
    }
}
