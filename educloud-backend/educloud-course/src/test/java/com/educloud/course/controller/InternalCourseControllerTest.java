package com.educloud.course.controller;

import com.baomidou.mybatisplus.core.conditions.ISqlSegment;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.course.config.CourseProperties;
import com.educloud.course.config.SecurityConfig;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseTeacherMapper;
import com.educloud.course.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M05 任务 17：内部课程访问接口测试（GET /internal/v1/courses/{id}）。
 *
 * <p>依据：规格 §9 —— 内部控制器只经 {@code InternalApiFilter.requireClientId} 校验，
 * 不配 @PreAuthorize（服务令牌无 permissions claim）。无服务令牌 → 401；错误
 * aud/未知 clientId → 403（InternalApiFilter 行为，先于 Security 链）；课程不存在
 * → 404 COURSE_NOT_FOUND；正常响应返回归属/可见性快照（publishedVersionId /
 * draftVersionId / ownerTeacherId / contentReady=false 占位 / teachers），供 M06
 * content 消费。MockMvc + mock JwtDecoder/Mapper（对齐任务 6 测试模式与
 * InternalFileControllerTest）。</p>
 */
@WebMvcTest(controllers = InternalCourseController.class)
@Import({SecurityConfig.class, InternalCourseControllerTest.TestInfrastructure.class})
class InternalCourseControllerTest {

    private static final long COURSE_ID = 101L;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // 纯 Mockito 切片单测无 Mapper 注册，LambdaWrapper 渲染列名依赖 TableInfo（与
        // 服务层测试同一共享支持类）。
        MybatisPlusTestSupport.registerTableInfo(CourseTeacherEntity.class);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private CourseMapper courseMapper;

    @MockBean
    private CourseTeacherMapper courseTeacherMapper;

    @Test
    void rejectsMissingServiceTokenWith401() throws Exception {
        mockMvc.perform(get("/internal/v1/courses/{courseId}", COURSE_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(courseMapper, courseTeacherMapper);
    }

    @Test
    void rejectsUnknownClientIdWith403() throws Exception {
        when(jwtDecoder.decode("evil-token")).thenReturn(serviceToken("evil-service", "educloud-course"));

        mockMvc.perform(get("/internal/v1/courses/{courseId}", COURSE_ID)
                        .header("Authorization", "Bearer evil-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(courseMapper, courseTeacherMapper);
    }

    @Test
    void rejectsWrongAudienceWith403() throws Exception {
        when(jwtDecoder.decode("wrong-aud-token")).thenReturn(serviceToken("educloud-content", "educloud-file"));

        mockMvc.perform(get("/internal/v1/courses/{courseId}", COURSE_ID)
                        .header("Authorization", "Bearer wrong-aud-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(courseMapper, courseTeacherMapper);
    }

    @Test
    void returnsAccessSnapshotForKnownCourse() throws Exception {
        when(jwtDecoder.decode("content-token"))
                .thenReturn(serviceToken("educloud-content", "educloud-course"));
        when(courseMapper.selectById(COURSE_ID)).thenReturn(course());
        when(courseTeacherMapper.selectList(any())).thenReturn(List.of(owner(), coTeacher()));

        mockMvc.perform(get("/internal/v1/courses/{courseId}", COURSE_ID)
                        .header("Authorization", "Bearer content-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value("101"))
                .andExpect(jsonPath("$.lifecycleStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedVersionId").value("9001"))
                .andExpect(jsonPath("$.draftVersionId").value("9002"))
                .andExpect(jsonPath("$.ownerTeacherId").value("1001"))
                .andExpect(jsonPath("$.contentReady").value(false))
                .andExpect(jsonPath("$.teachers[0].teacherId").value("1001"))
                .andExpect(jsonPath("$.teachers[0].teacherRole").value("OWNER"))
                .andExpect(jsonPath("$.teachers[1].teacherId").value("1002"))
                .andExpect(jsonPath("$.teachers[1].teacherRole").value("CO_TEACHER"));

        // 归属查询 wrapper：按课程过滤且按 joined_at 升序（负责人优先于共同授课的稳定次序）。
        ArgumentCaptor<Wrapper<CourseTeacherEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(courseTeacherMapper).selectList(captor.capture());
        LambdaQueryWrapper<CourseTeacherEntity> wrapper =
                (LambdaQueryWrapper<CourseTeacherEntity>) captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("course_id");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(COURSE_ID);
        assertThat(wrapper.getExpression().getOrderBy())
                .extracting(ISqlSegment::getSqlSegment)
                .containsExactly("joined_at ASC");
    }

    @Test
    void returnsCourseNotFoundForMissingCourse() throws Exception {
        when(jwtDecoder.decode("content-token"))
                .thenReturn(serviceToken("educloud-content", "educloud-course"));
        when(courseMapper.selectById(COURSE_ID)).thenReturn(null);

        mockMvc.perform(get("/internal/v1/courses/{courseId}", COURSE_ID)
                        .header("Authorization", "Bearer content-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    private static CourseEntity course() {
        CourseEntity course = new CourseEntity();
        course.setId(COURSE_ID);
        course.setOwnerTeacherId(1001L);
        course.setLifecycleStatus("PUBLISHED");
        course.setPublishedVersionId(9001L);
        course.setDraftVersionId(9002L);
        return course;
    }

    private static CourseTeacherEntity owner() {
        CourseTeacherEntity teacher = new CourseTeacherEntity();
        teacher.setCourseId(COURSE_ID);
        teacher.setTeacherId(1001L);
        teacher.setTeacherRole("OWNER");
        teacher.setJoinedAt(LocalDateTime.of(2026, 8, 20, 10, 0));
        return teacher;
    }

    private static CourseTeacherEntity coTeacher() {
        CourseTeacherEntity teacher = new CourseTeacherEntity();
        teacher.setCourseId(COURSE_ID);
        teacher.setTeacherId(1002L);
        teacher.setTeacherRole("CO_TEACHER");
        teacher.setJoinedAt(LocalDateTime.of(2026, 8, 21, 10, 0));
        return teacher;
    }

    private static Jwt serviceToken(String clientId, String audience) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("clientId", clientId)
                .audience(List.of(audience))
                .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestInfrastructure {

        @Bean
        MockMvcBuilderCustomizer internalServletPathCustomizer() {
            // 真实容器中 servletPath 与内部路由对齐；MockMvc 请求默认 servletPath 为空，
            // 而 InternalApiFilter.shouldNotFilter 按 getServletPath 判断，这里补齐映射
            // （对齐 InternalFileControllerTest 同款处理）。
            return builder -> builder.defaultRequest(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/")
                    .with(request -> {
                        if (request.getRequestURI().startsWith("/internal/v1/")) {
                            request.setServletPath(request.getRequestURI());
                        }
                        return request;
                    }));
        }

        @Bean
        CourseProperties courseProperties() {
            // clientId 白名单 fail-closed：仅 educloud-content（M06 调用方）放行。
            return new CourseProperties(
                    "test",
                    new CourseProperties.Jwt("", "https://issuer.educloud.local", "educloud-api"),
                    new CourseProperties.Internal(List.of("educloud-content"), "educloud-course"));
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-21T10:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        RequestIdPolicy requestIdPolicy() {
            return new RequestIdPolicy(UUID::randomUUID);
        }

        @Bean
        RequestContextAccessor requestContextAccessor(RequestIdPolicy requestIdPolicy) {
            return new ServletRequestContextAccessor(requestIdPolicy, null);
        }

        @Bean
        ApiResponseFactory apiResponseFactory(
                RequestContextAccessor requestContextAccessor, Clock clock) {
            return new ApiResponseFactory(requestContextAccessor, clock);
        }
    }
}
