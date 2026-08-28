package com.educloud.content.dto;

import com.educloud.content.dto.response.ExamQuestionResponse;
import com.educloud.content.dto.response.ExamResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 考试接口响应字段契约（规格 2026-08-28-educloud-exam-design.md §7 / §9）。
 *
 * 这里锁住两条容易被无意破坏的对外约定：
 * 1. 已批改态不下发 questions，题数必须由 questionCount 提供；
 * 2. 题目响应任何情况下都不得包含正确答案。
 */
class ExamApiContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void studentView_contract_exposesQuestionCountWithoutLeakingAnswers() throws Exception {
        ExamResponse view = ExamResponse.builder()
                .id(1L)
                .title("微服务架构认证考试")
                .status("GRADED")
                .questionCount(3)
                .questions(List.of(ExamQuestionResponse.builder()
                        .id(11L)
                        .questionType("SINGLE")
                        .stem("Spring Cloud 中用于服务注册与发现的核心组件是？")
                        .options(List.of("Nacos", "Ribbon"))
                        .score(10)
                        .build()))
                .build();

        String json = objectMapper.writeValueAsString(view);

        assertThat(json).contains("\"questionCount\":3");
        // 答案与解析字段一旦出现在 DTO 上就会随响应泄露，契约要求永不下发
        assertThat(json)
                .doesNotContain("answer")
                .doesNotContain("correctAnswer")
                .doesNotContain("analysis");
    }
}
