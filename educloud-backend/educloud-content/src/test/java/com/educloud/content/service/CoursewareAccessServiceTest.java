package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.response.CoursewareDownloadUrlResponse;
import com.educloud.content.entity.CoursewareEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.CoursewareMapper;
import com.educloud.content.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursewareAccessServiceTest {

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.registerTableInfo(CoursewareEntity.class);
    }

    @Mock
    private CoursewareMapper coursewareMapper;
    @Mock
    private FileClient fileClient;

    @InjectMocks
    private CoursewareAccessService accessService;

    @Test
    void getDownloadUrl_allowsAnonymousForFreePreview() {
        CoursewareEntity cw = new CoursewareEntity();
        cw.setId(501L);
        cw.setCourseId(101L);
        cw.setFileId(801L);
        cw.setFreePreview(true);
        cw.setStatus("ACTIVE");

        when(coursewareMapper.selectById(501L)).thenReturn(cw);
        when(fileClient.getDownloadUrl(801L, 501L, null)).thenReturn("http://presigned.minio/preview.mp4");

        CoursewareDownloadUrlResponse resp = accessService.getDownloadUrl(501L, null, Set.of(), Set.of());
        assertThat(resp).isNotNull();
        assertThat(resp.getDownloadUrl()).isEqualTo("http://presigned.minio/preview.mp4");
    }

    @Test
    void getDownloadUrl_deniesAnonymousForNonFreePreview() {
        CoursewareEntity cw = new CoursewareEntity();
        cw.setId(502L);
        cw.setCourseId(101L);
        cw.setFileId(802L);
        cw.setFreePreview(false);
        cw.setStatus("ACTIVE");

        when(coursewareMapper.selectById(502L)).thenReturn(cw);

        assertThatThrownBy(() -> accessService.getDownloadUrl(502L, null, Set.of(), Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContentErrorCode.COURSEWARE_ACCESS_DENIED);
    }
}
