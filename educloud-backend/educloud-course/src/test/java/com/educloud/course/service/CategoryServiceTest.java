package com.educloud.course.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.course.dto.response.CategoryResponse;
import com.educloud.course.entity.CourseCategoryEntity;
import com.educloud.course.mapper.CourseCategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M05 任务 7：分类查询服务单元测试（可见分类树排序、隐藏分类过滤、VISIBLE 查询条件）。
 * 依据：任务 7 步骤 3 —— 查询 status=VISIBLE 全量，内存组树按 sort_order。
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CourseCategoryMapper categoryMapper;

    @Test
    void visibleTreeSortsTopLevelAndChildrenBySortOrder() {
        CourseCategoryEntity backend = category(1L, null, "后端", "backend", 2, "VISIBLE");
        CourseCategoryEntity frontend = category(2L, null, "前端", "frontend", 1, "VISIBLE");
        CourseCategoryEntity java = category(3L, 2L, "Java", "java", 2, "VISIBLE");
        CourseCategoryEntity spring = category(4L, 2L, "Spring", "spring", 1, "VISIBLE");
        // 故意乱序返回：排序必须由服务在内存完成，而非依赖 DB 顺序。
        when(categoryMapper.selectList(any())).thenReturn(List.of(backend, java, spring, frontend));

        List<CategoryResponse> tree = service().visibleTree();

        assertThat(tree).extracting(CategoryResponse::name).containsExactly("前端", "后端");
        assertThat(tree.get(0).children()).extracting(CategoryResponse::name).containsExactly("Spring", "Java");
        assertThat(tree.get(0).children().get(0).slug()).isEqualTo("spring");
        assertThat(tree.get(0).children().get(0).id()).isEqualTo("4");
        assertThat(tree.get(0).children().get(0).sortOrder()).isEqualTo(1);
        assertThat(tree.get(1).children()).isEmpty();
    }

    @Test
    void visibleTreeFiltersHiddenCategories() {
        CourseCategoryEntity visible = category(1L, null, "可见分类", "visible", 1, "VISIBLE");
        CourseCategoryEntity hiddenTop = category(2L, null, "隐藏分类", "hidden", 2, "HIDDEN");
        CourseCategoryEntity hiddenChild = category(3L, 1L, "隐藏子分类", "hidden-child", 1, "HIDDEN");
        when(categoryMapper.selectList(any())).thenReturn(List.of(hiddenTop, visible, hiddenChild));

        List<CategoryResponse> tree = service().visibleTree();

        assertThat(tree).extracting(CategoryResponse::name).containsExactly("可见分类");
        assertThat(tree.get(0).children()).isEmpty();
    }

    @Test
    void visibleTreeDropsVisibleChildWhoseParentIsHidden() {
        CourseCategoryEntity hiddenParent = category(1L, null, "隐藏父分类", "hidden-parent", 1, "HIDDEN");
        CourseCategoryEntity visibleChild = category(2L, 1L, "可见子分类", "visible-child", 1, "VISIBLE");
        when(categoryMapper.selectList(any())).thenReturn(List.of(hiddenParent, visibleChild));

        List<CategoryResponse> tree = service().visibleTree();

        assertThat(tree).isEmpty();
    }

    @Test
    void visibleTreeDropsVisibleNodeWhoseParentDoesNotExist() {
        CourseCategoryEntity orphan = category(9L, 888L, "孤儿节点", "orphan", 1, "VISIBLE");
        when(categoryMapper.selectList(any())).thenReturn(List.of(orphan));

        List<CategoryResponse> tree = service().visibleTree();

        assertThat(tree).isEmpty();
    }

    @Test
    void visibleTreeQueriesOnlyVisibleStatus() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());

        service().visibleTree();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<QueryWrapper<CourseCategoryEntity>> captor =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(categoryMapper).selectList(captor.capture());
        QueryWrapper<CourseCategoryEntity> wrapper = captor.getValue();
        // 注意：MP 3.5.12 的 paramNameValuePairs 在 getSqlSegment() 渲染后才物化，先渲染再断言。
        // 整串匹配保证查询唯一条件且列名精确为 status（避免 x_status 之类误匹配）。
        assertThat(wrapper.getSqlSegment())
                .matches("\\(status\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.[A-Za-z0-9]+}\\)");
        assertThat(wrapper.getParamNameValuePairs()).containsValue("VISIBLE");
    }

    private CategoryService service() {
        return new CategoryService(categoryMapper);
    }

    private static CourseCategoryEntity category(
            Long id, Long parentId, String name, String slug, int sortOrder, String status) {
        CourseCategoryEntity entity = new CourseCategoryEntity();
        entity.setId(id);
        entity.setParentId(parentId);
        entity.setName(name);
        entity.setSlug(slug);
        entity.setSortOrder(sortOrder);
        entity.setStatus(status);
        return entity;
    }
}
