package com.educloud.course.mapper;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理端课程管理列表投影行（CourseMapper.selectAdminCoursesPage，M05 任务 23）。
 *
 * <p>course JOIN course_version(COALESCE(draft,published,最新)) 一次取回列表项所需列：
 * 当前工作版本的标题/封面/难度/价格/分类与版本状态，以及课程根的 lifecycle_status/
 * enrollment_count。cover_file_id 经 FileClient 批量 grant（subject=USER 当前管理员）
 * 组装 coverUrl——管理员查看任意教师课程封面，不签匿名公开 URL。</p>
 */
@Data
public class AdminCourseRow {

    private Long courseId;

    private Long versionId;

    private Integer versionNo;

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
