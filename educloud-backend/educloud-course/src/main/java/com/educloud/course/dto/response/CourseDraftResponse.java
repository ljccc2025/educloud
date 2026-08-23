package com.educloud.course.dto.response;

import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseVersionEntity;

import java.util.List;

/**
 * 课程草稿响应（M05 任务 8/22）：POST /courses、GET draft、POST drafts、PUT draft 共用。
 *
 * <p>Snowflake ID 一律 String（M04 坑 1：前端禁止 Number()）；price 金额序列化为
 * 字符串（十进制金额，避免 JSON number 精度歧义，任务 11 公开列表沿用同一风格）。
 * teachers 为课程教师快照（teacherId + role，负责人+共同授课）。coverUrl（任务 22 补）：
 * 教师视角封面 URL——经 FileClient 批量 grant（subject=USER 教师本人）组装，无封面或
 * 不可达时 null，前端用占位图回显。</p>
 */
public record CourseDraftResponse(
        String courseId,
        String versionId,
        Integer versionNo,
        String title,
        String subtitle,
        String description,
        String coverFileId,
        String coverUrl,
        String level,
        String price,
        String currency,
        String categoryId,
        String versionStatus,
        String lifecycleStatus,
        List<Teacher> teachers) {

    /** 教师成员：teacherId（String）+ role（OWNER/CO_TEACHER）。 */
    public record Teacher(String teacherId, String role) {
    }

    public static CourseDraftResponse from(
            CourseEntity course, CourseVersionEntity version, List<Teacher> teachers, String coverUrl) {
        return new CourseDraftResponse(
                String.valueOf(course.getId()),
                String.valueOf(version.getId()),
                version.getVersionNo(),
                version.getTitle(),
                version.getSubtitle(),
                version.getDescription(),
                version.getCoverFileId() == null ? null : String.valueOf(version.getCoverFileId()),
                coverUrl,
                version.getLevel(),
                version.getPrice() == null ? null : version.getPrice().toPlainString(),
                version.getCurrency(),
                version.getCategoryId() == null ? null : String.valueOf(version.getCategoryId()),
                version.getVersionStatus(),
                course.getLifecycleStatus(),
                teachers);
    }
}
