package com.educloud.course.mapper;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 教师课程管理列表投影行（CourseMapper.selectTeacherCoursesPage，M05 任务 22）。
 *
 * <p>course_teacher JOIN course JOIN course_version(COALESCE(draft,published)) 一次取回
 * 列表项所需列：当前工作版本（草稿优先，无草稿时回落到发布版本）的标题/封面/难度/价格/
 * 分类与版本状态，以及课程根的 lifecycle_status/enrollment_count。cover_file_id 经
 * FileClient 批量 grant（subject=USER 教师本人）组装 coverUrl——教师查看自己的课程封面，
 * 不签匿名公开 URL。</p>
 */
@Data
public class CourseTeacherRow {

    private Long courseId;

    private Long versionId;

    private String lifecycleStatus;

    private String versionStatus;

    private String title;

    private Long coverFileId;

    private String level;

    private BigDecimal price;

    private String currency;

    private Long categoryId;

    private Integer enrollmentCount;
}
