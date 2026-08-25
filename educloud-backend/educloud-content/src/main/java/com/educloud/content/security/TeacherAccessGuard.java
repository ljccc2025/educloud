package com.educloud.content.security;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.content.entity.ChapterEntity;
import com.educloud.content.entity.ContentRevisionEntity;
import com.educloud.content.entity.CoursewareEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.ChapterMapper;
import com.educloud.content.mapper.ContentRevisionMapper;
import com.educloud.content.mapper.CoursewareMapper;
import com.educloud.content.service.CourseClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 教师端内容操作鉴权（BUG-005 修复）：角色/权限 + 课程归属双重校验。
 *
 * <p>归属校验经 {@link CourseClient} 调 course 内部接口（ownerTeacherId /
 * course_teacher 成员），阻止教师横向越权操作非本人课程的内容；平台管理员
 * （SYSTEM_ADMIN/SUPER_ADMIN/content:manage）豁免归属校验。chapterId /
 * coursewareId / revisionId 路径先解析出所属 courseId 再校验，实体不存在时
 * 抛对应 NOT_FOUND（fail-closed，不泄露差异）。</p>
 */
@Component
public class TeacherAccessGuard {

    private final ChapterMapper chapterMapper;
    private final CoursewareMapper coursewareMapper;
    private final ContentRevisionMapper revisionMapper;
    private final CourseClient courseClient;

    public TeacherAccessGuard(
            ChapterMapper chapterMapper,
            CoursewareMapper coursewareMapper,
            ContentRevisionMapper revisionMapper,
            CourseClient courseClient) {
        this.chapterMapper = chapterMapper;
        this.coursewareMapper = coursewareMapper;
        this.revisionMapper = revisionMapper;
        this.courseClient = courseClient;
    }

    public Long checkTeacherAccess(Jwt jwt) {
        if (jwt == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED, "Authentication required");
        }
        Long userId = JwtSecurityUtils.userId(jwt);
        Set<String> roles = JwtSecurityUtils.roles(jwt);
        Set<String> permissions = JwtSecurityUtils.permissions(jwt);

        boolean isTeacherOrAdmin = roles.contains("TEACHER") || roles.contains("SYSTEM_ADMIN") || roles.contains("SUPER_ADMIN");
        boolean hasManagePerm = permissions.contains("content:manage") || permissions.contains("course:update") || permissions.contains("course:create");

        if (!isTeacherOrAdmin && !hasManagePerm) {
            throw new BusinessException(ContentErrorCode.TEACHER_ACCESS_DENIED, "Teacher permission required");
        }
        return userId;
    }

    /** 角色/权限 + 课程归属校验（BUG-005）：非管理员教师必须是课程 OWNER 或成员。 */
    public Long checkTeacherAccess(Jwt jwt, Long courseId) {
        Long userId = checkTeacherAccess(jwt);

        Set<String> roles = JwtSecurityUtils.roles(jwt);
        Set<String> permissions = JwtSecurityUtils.permissions(jwt);
        boolean isAdmin = roles.contains("SYSTEM_ADMIN") || roles.contains("SUPER_ADMIN")
                || permissions.contains("content:manage");
        if (!isAdmin && !courseClient.isCourseTeacher(courseId, userId)) {
            throw new BusinessException(ContentErrorCode.TEACHER_ACCESS_DENIED,
                    "You are not a teacher of this course");
        }
        return userId;
    }

    /** 按 chapterId 解析所属课程后校验（章节归属课程，课件同用）。 */
    public Long checkTeacherAccessByChapter(Jwt jwt, Long chapterId) {
        ChapterEntity chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || "DELETED".equals(chapter.getStatus())) {
            throw new BusinessException(ContentErrorCode.CHAPTER_NOT_FOUND, "Chapter not found");
        }
        return checkTeacherAccess(jwt, chapter.getCourseId());
    }

    /** 按 coursewareId 解析所属课程后校验。 */
    public Long checkTeacherAccessByCourseware(Jwt jwt, Long coursewareId) {
        CoursewareEntity courseware = coursewareMapper.selectById(coursewareId);
        if (courseware == null || "DELETED".equals(courseware.getStatus())) {
            throw new BusinessException(ContentErrorCode.COURSEWARE_NOT_FOUND, "Courseware not found");
        }
        return checkTeacherAccess(jwt, courseware.getCourseId());
    }

    /** 按 contentRevisionId 解析所属课程后校验。 */
    public Long checkTeacherAccessByRevision(Jwt jwt, Long revisionId) {
        ContentRevisionEntity revision = revisionMapper.selectById(revisionId);
        if (revision == null) {
            throw new BusinessException(ContentErrorCode.REVISION_NOT_FOUND, "Content revision not found");
        }
        return checkTeacherAccess(jwt, revision.getCourseId());
    }
}
