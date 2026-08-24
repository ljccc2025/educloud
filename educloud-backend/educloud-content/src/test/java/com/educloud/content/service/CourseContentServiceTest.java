package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.content.dto.response.ContentDraftResponse;
import com.educloud.content.entity.ChapterEntity;
import com.educloud.content.entity.ContentRevisionEntity;
import com.educloud.content.entity.CourseContentEntity;
import com.educloud.content.entity.CoursewareEntity;
import com.educloud.content.entity.UserCoursewareProgressEntity;
import com.educloud.content.mapper.ChapterMapper;
import com.educloud.content.mapper.ContentRevisionMapper;
import com.educloud.content.mapper.CourseContentMapper;
import com.educloud.content.mapper.CoursewareMapper;
import com.educloud.content.mapper.UserCoursewareProgressMapper;
import com.educloud.content.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseContentServiceTest {

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.registerTableInfo(
                CourseContentEntity.class,
                ContentRevisionEntity.class,
                ChapterEntity.class,
                CoursewareEntity.class,
                UserCoursewareProgressEntity.class);
    }

    @Mock
    private CourseContentMapper contentMapper;
    @Mock
    private ContentRevisionMapper revisionMapper;
    @Mock
    private ChapterMapper chapterMapper;
    @Mock
    private CoursewareMapper coursewareMapper;
    @Mock
    private UserCoursewareProgressMapper progressMapper;
    @Mock
    private IdentifierGenerator idGenerator;

    @InjectMocks
    private CourseContentService courseContentService;

    @Test
    void getOrCreateDraft_createsNewRootAndRevisionWhenNoneExist() {
        when(idGenerator.nextId()).thenReturn(1001L, 1002L);
        when(contentMapper.selectOne(any())).thenReturn(null);
        when(revisionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(chapterMapper.selectList(any())).thenReturn(Collections.emptyList());

        ContentDraftResponse draft = courseContentService.getOrCreateDraft(100L, 9001L);

        assertThat(draft).isNotNull();
        assertThat(draft.getContentRootId()).isEqualTo(1001L);
        assertThat(draft.getRevisionId()).isEqualTo(1002L);
        assertThat(draft.getRevisionNo()).isEqualTo(1);
        assertThat(draft.getRevisionStatus()).isEqualTo("DRAFT");
        verify(contentMapper).insert(any(CourseContentEntity.class));
        verify(revisionMapper).insert(any(ContentRevisionEntity.class));
    }
}
