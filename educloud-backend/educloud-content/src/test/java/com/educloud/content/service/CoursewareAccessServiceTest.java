package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.response.CoursewareDownloadUrlResponse;
import com.educloud.content.entity.CourseContentEntity;
import com.educloud.content.entity.CoursewareEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.CourseContentMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursewareAccessServiceTest {

    @BeforeAll
    static void initMybatisPlus() {
        // BUG-003 的已发布版本校验使用 CourseContentEntity LambdaWrapper。
        MybatisPlusTestSupport.registerTableInfo(CoursewareEntity.class, CourseContentEntity.class);
    }

    @Mock
    private CoursewareMapper coursewareMapper;
    @Mock
    private CourseContentMapper courseContentMapper;
    @Mock
    private FileClient fileClient;
    @Mock
    private CourseClient courseClient;

    @InjectMocks
    private CoursewareAccessService accessService;

    /** BUG-003：内容根已发布版本与课件所属版本一致（非 staff 下载前置校验）。 */
    private static CourseContentEntity publishedRoot(Long courseId, Long revisionId) {
        CourseContentEntity root = new CourseContentEntity();
        root.setId(301L);
        root.setCourseId(courseId);
        root.setPublishedRevisionId(revisionId);
        return root;
    }

    @Test
    void getDownloadUrl_allowsAnonymousForFreePreview() {
        CoursewareEntity cw = new CoursewareEntity();
        cw.setId(501L);
        cw.setCourseId(101L);
        cw.setFileId(801L);
        cw.setFreePreview(true);
        cw.setStatus("ACTIVE");
        cw.setContentRevisionId(701L);

        when(coursewareMapper.selectById(501L)).thenReturn(cw);
        when(courseContentMapper.selectOne(any())).thenReturn(publishedRoot(101L, 701L));
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
        cw.setContentRevisionId(701L);

        when(coursewareMapper.selectById(502L)).thenReturn(cw);
        when(courseContentMapper.selectOne(any())).thenReturn(publishedRoot(101L, 701L));

        // BUG-002：匿名用户无报名权益 → fail-closed 拒绝。
        assertThatThrownBy(() -> accessService.getDownloadUrl(502L, null, Set.of(), Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContentErrorCode.COURSEWARE_ACCESS_DENIED);
    }

    /** BUG-002：已登录但未报名同样拒绝（权益权威来自 course 服务）。 */
    @Test
    void getDownloadUrl_deniesLoggedInStudentWithoutEnrollment() {
        CoursewareEntity cw = new CoursewareEntity();
        cw.setId(503L);
        cw.setCourseId(101L);
        cw.setFileId(803L);
        cw.setFreePreview(false);
        cw.setStatus("ACTIVE");
        cw.setContentRevisionId(701L);

        when(coursewareMapper.selectById(503L)).thenReturn(cw);
        when(courseContentMapper.selectOne(any())).thenReturn(publishedRoot(101L, 701L));
        when(courseClient.isEnrolled(101L, 2001L)).thenReturn(false);

        assertThatThrownBy(() -> accessService.getDownloadUrl(503L, 2001L, Set.of("STUDENT"), Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContentErrorCode.COURSEWARE_ACCESS_DENIED);
    }

    /** BUG-003：课件属于未发布版本时拒绝下载（即便已报名）。 */
    @Test
    void getDownloadUrl_deniesUnpublishedRevisionCourseware() {
        CoursewareEntity cw = new CoursewareEntity();
        cw.setId(504L);
        cw.setCourseId(101L);
        cw.setFileId(804L);
        cw.setFreePreview(false);
        cw.setStatus("ACTIVE");
        cw.setContentRevisionId(799L);

        when(coursewareMapper.selectById(504L)).thenReturn(cw);
        when(courseContentMapper.selectOne(any())).thenReturn(publishedRoot(101L, 701L));

        assertThatThrownBy(() -> accessService.getDownloadUrl(504L, 2001L, Set.of("STUDENT"), Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContentErrorCode.COURSEWARE_ACCESS_DENIED);
    }
}
