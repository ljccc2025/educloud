package com.educloud.search.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseIndexDocTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("测试 LessonDoc 字段与构建")
    void testLessonDoc() {
        LessonDoc lesson = LessonDoc.builder()
                .id("7000000000000000001")
                .title("1.1 架构设计总览")
                .chapterTitle("第1章 基础概念")
                .isPreview(true)
                .build();

        assertThat(lesson.getId()).isEqualTo("7000000000000000001");
        assertThat(lesson.getTitle()).isEqualTo("1.1 架构设计总览");
        assertThat(lesson.getChapterTitle()).isEqualTo("第1章 基础概念");
        assertThat(lesson.getIsPreview()).isTrue();
    }

    @Test
    @DisplayName("测试 CourseIndexDoc 序列化与反序列化（含雪花ID字符串与嵌套课件）")
    void testCourseIndexDocSerialization() throws Exception {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 8, 25, 10, 30, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 26, 12, 0, 0);

        LessonDoc lesson1 = LessonDoc.builder()
                .id("7000000000000000001")
                .title("1.1 架构设计总览")
                .chapterTitle("第1章 基础概念")
                .isPreview(true)
                .build();

        LessonDoc lesson2 = LessonDoc.builder()
                .id("7000000000000000002")
                .title("1.2 服务拆分原则")
                .chapterTitle("第1章 基础概念")
                .isPreview(false)
                .build();

        CourseIndexDoc doc = CourseIndexDoc.builder()
                .id("2091648316809035780")
                .courseId("2091648316809035780")
                .title("Spring Cloud 微服务实战开发")
                .subtitle("从零构建高可用企业级云原生架构")
                .description("全面掌握微服务架构核心要素与高并发设计")
                .teacherId("9000000000000000001")
                .teacherName("李明远")
                .category("后端开发")
                .categoryCode("BACKEND")
                .coverUrl("https://oss.educloud.local/covers/course-1.jpg")
                .difficulty("INTERMEDIATE")
                .priceCents(19900L)
                .isFree(false)
                .rating(4.9f)
                .studentCount(1280)
                .lessonCount(2)
                .status("PUBLISHED")
                .tags(List.of("Spring Boot", "微服务", "Docker"))
                .lessons(List.of(lesson1, lesson2))
                .aggregateVersion(5L)
                .publishedAt(publishedAt)
                .updatedAt(updatedAt)
                .build();

        String json = objectMapper.writeValueAsString(doc);
        assertThat(json).contains("\"id\":\"2091648316809035780\"");
        assertThat(json).contains("\"courseId\":\"2091648316809035780\"");
        assertThat(json).contains("\"title\":\"Spring Cloud 微服务实战开发\"");
        assertThat(json).contains("\"lessons\":[");
        assertThat(json).contains("\"tags\":[\"Spring Boot\",\"微服务\",\"Docker\"]");

        CourseIndexDoc parsed = objectMapper.readValue(json, CourseIndexDoc.class);
        assertThat(parsed.getId()).isEqualTo("2091648316809035780");
        assertThat(parsed.getCourseId()).isEqualTo("2091648316809035780");
        assertThat(parsed.getTitle()).isEqualTo("Spring Cloud 微服务实战开发");
        assertThat(parsed.getSubtitle()).isEqualTo("从零构建高可用企业级云原生架构");
        assertThat(parsed.getTeacherName()).isEqualTo("李明远");
        assertThat(parsed.getCategoryCode()).isEqualTo("BACKEND");
        assertThat(parsed.getPriceCents()).isEqualTo(19900L);
        assertThat(parsed.getIsFree()).isFalse();
        assertThat(parsed.getRating()).isEqualTo(4.9f);
        assertThat(parsed.getStudentCount()).isEqualTo(1280);
        assertThat(parsed.getLessonCount()).isEqualTo(2);
        assertThat(parsed.getTags()).containsExactly("Spring Boot", "微服务", "Docker");
        assertThat(parsed.getLessons()).hasSize(2);
        assertThat(parsed.getLessons().get(0).getTitle()).isEqualTo("1.1 架构设计总览");
        assertThat(parsed.getLessons().get(0).getIsPreview()).isTrue();
        assertThat(parsed.getLessons().get(1).getIsPreview()).isFalse();
        assertThat(parsed.getAggregateVersion()).isEqualTo(5L);
        assertThat(parsed.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(parsed.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
