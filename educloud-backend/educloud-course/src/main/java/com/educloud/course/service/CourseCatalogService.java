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
import com.educloud.course.support.SnowflakeIds;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程公开列表与详情服务（M05 任务 11）。
 *
 * <p>列表：SQL 分页（CourseMapper.selectCatalogPage JOIN 查询）+ keyword/categoryId/
 * level/priceRange 过滤 + 排序白名单映射（非法 sort → 400 VALIDATION_FAILED，缺省
 * popular）；enrollment_count/rating 取 course 聚合列；enrolled 用当前 userId 批量
 * IN 查询 course_enrollment（ACTIVE），避免 N+1。coverUrl 恒 null（任务 12 File grant
 * 后填充）；teacherName 以 teacherId 字符串占位（M05 无 user Profile 客户端）。</p>
 *
 * <p>详情可见性：PUBLISHED 公开；DRAFT/PENDING_REVIEW/OFFLINE 仅归属教师
 * （course_teacher 存在行，OWNER 视角下发 lifecycleStatus）；ARCHIVED 与匿名/越权
 * 请求一律 404 COURSE_NOT_FOUND（规格 §6「他人/未登录看非 PUBLISHED → 404」；
 * OFFLINE 教师可见但列表不出现）。reviews 本任务恒空列表（任务 14 接评价）。</p>
 */
@Service
public class CourseCatalogService {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private static final String SORT_POPULAR = "popular";
    private static final String LIFECYCLE_PUBLISHED = "PUBLISHED";
    private static final String LIFECYCLE_OFFLINE = "OFFLINE";
    private static final String ENROLLMENT_ACTIVE = "ACTIVE";

    /** 排序白名单（规格 §6）；白名单外 → 400。 */
    private static final Set<String> SORT_WHITELIST = Set.of(
            "popular", "newest", "price-asc", "price-desc", "rating");

    /** priceRange 枚举（规格 §6）；枚举外 → 400。 */
    private static final Set<String> PRICE_RANGE_WHITELIST = Set.of(
            "free", "under200", "200to400", "above400");

    /** 非公开但归属教师可经详情查看的状态；ARCHIVED 终态对所有人 404。 */
    private static final Set<String> OWNER_VIEW_STATUSES = Set.of(
            "DRAFT", "PENDING_REVIEW", "OFFLINE");

    private final CourseMapper courseMapper;
    private final CourseVersionMapper versionMapper;
    private final CourseEnrollmentMapper enrollmentMapper;
    private final CourseCategoryMapper categoryMapper;
    private final CourseTeacherMapper teacherMapper;

    public CourseCatalogService(
            CourseMapper courseMapper,
            CourseVersionMapper versionMapper,
            CourseEnrollmentMapper enrollmentMapper,
            CourseCategoryMapper categoryMapper,
            CourseTeacherMapper teacherMapper) {
        this.courseMapper = Objects.requireNonNull(courseMapper, "courseMapper");
        this.versionMapper = Objects.requireNonNull(versionMapper, "versionMapper");
        this.enrollmentMapper = Objects.requireNonNull(enrollmentMapper, "enrollmentMapper");
        this.categoryMapper = Objects.requireNonNull(categoryMapper, "categoryMapper");
        this.teacherMapper = Objects.requireNonNull(teacherMapper, "teacherMapper");
    }

    /** 公开列表（匿名可达）：仅 PUBLISHED；enrolled 需登录态（无 token → false）。 */
    public PageResponse<CourseSummaryResponse> list(CourseListQuery query, Long currentUserId) {
        Objects.requireNonNull(query, "query");
        String sort = normalizeSort(query.sort());
        String priceRange = normalizePriceRange(query.priceRange());
        Long categoryId = SnowflakeIds.parse(query.categoryId(), "categoryId");
        String keyword = trimToNull(query.keyword());
        String level = trimToNull(query.level());
        int pageNum = query.page() == null ? DEFAULT_PAGE : Math.max(query.page(), 1);
        int pageSize = query.size() == null
                ? DEFAULT_SIZE
                : Math.min(Math.max(query.size(), 1), MAX_SIZE);

        Page<CourseCatalogRow> pageRequest = new Page<>(pageNum, pageSize);
        List<CourseCatalogRow> rows = courseMapper.selectCatalogPage(
                pageRequest, keyword, categoryId, level, priceRange, sort).getRecords();

        Set<Long> enrolledCourseIds = (currentUserId == null || rows.isEmpty())
                ? Set.of()
                : enrolledCourseIds(rows, currentUserId);

        List<CourseSummaryResponse> items = rows.stream()
                .map(row -> toSummary(row, enrolledCourseIds.contains(row.getCourseId())))
                .toList();
        return PageResponse.of(items, pageNum, pageSize, pageRequest.getTotal());
    }

    /**
     * 课程详情（按可见性）：PUBLISHED 公开；DRAFT/PENDING_REVIEW/OFFLINE 仅归属教师；
     * ARCHIVED/匿名/越权 → 404 COURSE_NOT_FOUND。
     */
    public CourseDetailResponse detail(Long courseId, Long currentUserId) {
        CourseEntity course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course not found: " + courseId);
        }
        String lifecycle = course.getLifecycleStatus();
        boolean publicCourse = LIFECYCLE_PUBLISHED.equals(lifecycle);
        if (!publicCourse) {
            requireOwnerView(course, currentUserId);
        }
        // PUBLISHED/OFFLINE 跟随 published_version_id；DRAFT/PENDING_REVIEW 读草稿指针。
        Long versionId = (publicCourse || LIFECYCLE_OFFLINE.equals(lifecycle))
                ? course.getPublishedVersionId()
                : course.getDraftVersionId();
        CourseVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course has no readable version: " + courseId);
        }
        CourseCategoryEntity category = categoryMapper.selectById(version.getCategoryId());
        List<CourseTeacherEntity> teachers = teacherMapper.selectList(
                new LambdaQueryWrapper<CourseTeacherEntity>()
                        .eq(CourseTeacherEntity::getCourseId, courseId)
                        .orderByAsc(CourseTeacherEntity::getJoinedAt));
        return toDetail(course, version, category, teachers, isEnrolled(courseId, currentUserId));
    }

    /** 非公开课程：仅归属教师（course_teacher 行存在），否则 404（不暴露存在性）。 */
    private void requireOwnerView(CourseEntity course, Long currentUserId) {
        if (!OWNER_VIEW_STATUSES.contains(course.getLifecycleStatus()) || currentUserId == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course is not publicly visible: " + course.getId());
        }
        Long ownerCount = teacherMapper.selectCount(new LambdaQueryWrapper<CourseTeacherEntity>()
                .eq(CourseTeacherEntity::getCourseId, course.getId())
                .eq(CourseTeacherEntity::getTeacherId, currentUserId));
        if (ownerCount == null || ownerCount == 0L) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course is not publicly visible: " + course.getId());
        }
    }

    /** 批量 enrolled 标记：一次 IN 查询当前用户 ACTIVE 选课，避免 N+1。 */
    private Set<Long> enrolledCourseIds(List<CourseCatalogRow> rows, Long currentUserId) {
        List<Long> courseIds = rows.stream().map(CourseCatalogRow::getCourseId).toList();
        List<CourseEnrollmentEntity> enrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<CourseEnrollmentEntity>()
                        .in(CourseEnrollmentEntity::getCourseId, courseIds)
                        .eq(CourseEnrollmentEntity::getStudentId, currentUserId)
                        .eq(CourseEnrollmentEntity::getStatus, ENROLLMENT_ACTIVE));
        return enrollments.stream()
                .map(CourseEnrollmentEntity::getCourseId)
                .collect(Collectors.toSet());
    }

    private boolean isEnrolled(Long courseId, Long currentUserId) {
        if (currentUserId == null) {
            return false;
        }
        Long count = enrollmentMapper.selectCount(new LambdaQueryWrapper<CourseEnrollmentEntity>()
                .eq(CourseEnrollmentEntity::getCourseId, courseId)
                .eq(CourseEnrollmentEntity::getStudentId, currentUserId)
                .eq(CourseEnrollmentEntity::getStatus, ENROLLMENT_ACTIVE));
        return count != null && count > 0L;
    }

    private static CourseSummaryResponse toSummary(CourseCatalogRow row, boolean enrolled) {
        return new CourseSummaryResponse(
                String.valueOf(row.getCourseId()),
                row.getTitle(),
                null,
                row.getTeacherId() == null ? null : String.valueOf(row.getTeacherId()),
                row.getCategoryName(),
                row.getLevel(),
                row.getPrice() == null ? null : row.getPrice().toPlainString(),
                row.getRatingAvg(),
                row.getRatingCount(),
                row.getEnrollmentCount(),
                enrolled);
    }

    private static CourseDetailResponse toDetail(
            CourseEntity course,
            CourseVersionEntity version,
            CourseCategoryEntity category,
            List<CourseTeacherEntity> teachers,
            boolean enrolled) {
        List<CourseDetailResponse.Teacher> teacherItems = teachers.stream()
                .map(teacher -> new CourseDetailResponse.Teacher(
                        String.valueOf(teacher.getTeacherId()), teacher.getTeacherRole()))
                .toList();
        return new CourseDetailResponse(
                String.valueOf(course.getId()),
                version.getTitle(),
                version.getSubtitle(),
                version.getDescription(),
                null,
                version.getLevel(),
                version.getPrice() == null ? null : version.getPrice().toPlainString(),
                version.getCurrency(),
                version.getCategoryId() == null ? null : String.valueOf(version.getCategoryId()),
                category == null ? null : category.getName(),
                teacherItems,
                course.getRatingAvg(),
                course.getRatingCount(),
                course.getEnrollmentCount(),
                enrolled,
                course.getLifecycleStatus(),
                List.of());
    }

    /** 排序白名单校验：null/空白 → popular；白名单外 → 400。 */
    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return SORT_POPULAR;
        }
        if (!SORT_WHITELIST.contains(sort)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED,
                    "sort must be one of " + String.join(", ", SORT_WHITELIST) + ": " + sort);
        }
        return sort;
    }

    /** priceRange 枚举校验：null/空白 → null（不过滤）；枚举外 → 400。 */
    private static String normalizePriceRange(String priceRange) {
        if (priceRange == null || priceRange.isBlank()) {
            return null;
        }
        if (!PRICE_RANGE_WHITELIST.contains(priceRange)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED,
                    "priceRange must be one of " + String.join(", ", PRICE_RANGE_WHITELIST)
                            + ": " + priceRange);
        }
        return priceRange;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
