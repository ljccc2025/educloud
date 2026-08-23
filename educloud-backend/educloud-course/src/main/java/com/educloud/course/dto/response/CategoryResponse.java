package com.educloud.course.dto.response;

import java.util.List;

/**
 * 课程分类响应（M05 任务 7）：雪花 ID 序列化为 String；children 递归组树；sortOrder 为展示排序。
 */
public record CategoryResponse(
        String id,
        String name,
        String slug,
        Integer sortOrder,
        List<CategoryResponse> children) {
}
