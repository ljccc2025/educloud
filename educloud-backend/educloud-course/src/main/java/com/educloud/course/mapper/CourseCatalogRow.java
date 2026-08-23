package com.educloud.course.mapper;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 公开列表查询投影行（CourseMapper.selectCatalogPage 的 JOIN 结果，M05 任务 11）。
 *
 * <p>course JOIN course_version(published_version_id) JOIN course_category JOIN
 * course_teacher(OWNER) 一次取回列表项所需列；MyBatis map-underscore-to-camel-case
 * 自动映射。教师展示名暂以 teacher_id 占位（M05 无 user Profile 客户端）。
 * cover_file_id 本任务不参与响应（coverUrl 恒 null），投影保留供任务 12 使用。</p>
 */
@Data
public class CourseCatalogRow {

    private Long courseId;

    private LocalDateTime publishedAt;

    private BigDecimal ratingAvg;

    private Integer ratingCount;

    private Integer enrollmentCount;

    private String title;

    private Long coverFileId;

    private String level;

    private BigDecimal price;

    private String currency;

    private Long categoryId;

    private String categoryName;

    private Long teacherId;
}
