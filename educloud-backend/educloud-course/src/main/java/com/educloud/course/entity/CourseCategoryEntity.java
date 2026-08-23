package com.educloud.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程分类（course_category）：slug 全局唯一；parent_id 自关联可空（分类树）；公开读取只返回 VISIBLE 分类。
 * 表名与 V001__course.sql 对齐；主键为雪花 ID（ASSIGN_ID，DB 无自增）。
 */
@Data
@TableName("course_category")
public class CourseCategoryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 父分类 ID（分类树自关联，可空）。 */
    private Long parentId;

    private String name;

    /** 全局唯一 slug（uk_course_category_slug）。 */
    private String slug;

    private Integer sortOrder;

    /** VISIBLE/HIDDEN（应用层枚举校验）。 */
    private String status;

    private Long createdBy;

    private LocalDateTime createdAt;

    private Long updatedBy;

    private LocalDateTime updatedAt;
}
