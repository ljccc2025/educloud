package com.educloud.course.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.course.dto.response.CategoryResponse;
import com.educloud.course.entity.CourseCategoryEntity;
import com.educloud.course.mapper.CourseCategoryMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程分类查询（M05 任务 7）。
 *
 * <p>公开只读：查询 status=VISIBLE 全量后在内存组树，顶层与子分类均按 sort_order 升序
 * （同序按 id 稳定）；隐藏分类（含隐藏父分类下的子分类）不出现在树中（服务层再做一次
 * status 过滤，双保险：SQL 过滤 + 内存过滤）。无分页（全量树）。</p>
 */
@Service
public class CategoryService {

    public static final String STATUS_VISIBLE = "VISIBLE";

    private static final Comparator<CourseCategoryEntity> BY_SORT_ORDER = Comparator
            .comparing(CourseCategoryEntity::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CourseCategoryEntity::getId);

    private final CourseCategoryMapper categoryMapper;

    public CategoryService(CourseCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public List<CategoryResponse> visibleTree() {
        List<CourseCategoryEntity> visible = categoryMapper.selectList(
                new QueryWrapper<CourseCategoryEntity>().eq("status", STATUS_VISIBLE));

        List<CourseCategoryEntity> sorted = visible.stream()
                .filter(entity -> STATUS_VISIBLE.equals(entity.getStatus()))
                .sorted(BY_SORT_ORDER)
                .toList();

        Map<Long, List<CourseCategoryEntity>> childrenByParent = new HashMap<>();
        for (CourseCategoryEntity entity : sorted) {
            childrenByParent.computeIfAbsent(entity.getParentId(), key -> new ArrayList<>()).add(entity);
        }

        List<CategoryResponse> roots = new ArrayList<>();
        for (CourseCategoryEntity entity : sorted) {
            if (entity.getParentId() == null) {
                roots.add(toResponse(entity, childrenByParent));
            }
        }
        return roots;
    }

    private static CategoryResponse toResponse(
            CourseCategoryEntity entity,
            Map<Long, List<CourseCategoryEntity>> childrenByParent) {
        List<CategoryResponse> children = new ArrayList<>();
        List<CourseCategoryEntity> childEntities = childrenByParent.get(entity.getId());
        if (childEntities != null) {
            for (CourseCategoryEntity child : childEntities) {
                children.add(toResponse(child, childrenByParent));
            }
        }
        return new CategoryResponse(
                String.valueOf(entity.getId()),
                entity.getName(),
                entity.getSlug(),
                entity.getSortOrder(),
                children);
    }
}
