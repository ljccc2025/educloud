package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.request.ExamQuestionRequest;
import com.educloud.content.dto.response.ExamBankQuestionResponse;
import com.educloud.content.entity.ExamBankQuestionEntity;
import com.educloud.content.entity.ExamEntity;
import com.educloud.content.entity.ExamPaperQuestionEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.ExamBankQuestionMapper;
import com.educloud.content.mapper.ExamMapper;
import com.educloud.content.mapper.ExamPaperQuestionMapper;
import com.educloud.content.support.MybatisPlusTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamBankServiceTest {

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.registerTableInfo(
                ExamBankQuestionEntity.class, ExamEntity.class, ExamPaperQuestionEntity.class);
    }

    @Mock
    private ExamBankQuestionMapper questionMapper;
    @Mock
    private ExamPaperQuestionMapper paperQuestionMapper;
    @Mock
    private ExamMapper examMapper;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ExamBankService examBankService;

    private static ExamQuestionRequest request() {
        ExamQuestionRequest req = new ExamQuestionRequest();
        req.setCourseId(1001L);
        req.setQuestionType("SINGLE");
        req.setStem("Spring Boot 默认端口？");
        req.setOptions(List.of("8080", "9090"));
        req.setAnswer(List.of(0));
        req.setAnalysis("默认端口是 8080");
        req.setDefaultScore(5);
        return req;
    }

    @Test
    void createQuestion_persistsJsonFields() {
        ArgumentCaptor<ExamBankQuestionEntity> captor = ArgumentCaptor.forClass(ExamBankQuestionEntity.class);

        ExamBankQuestionResponse response = examBankService.createQuestion(request(), 777L);

        verify(questionMapper).insert(captor.capture());
        ExamBankQuestionEntity saved = captor.getValue();
        assertThat(saved.getOptions()).isEqualTo("[\"8080\",\"9090\"]");
        assertThat(saved.getAnswer()).isEqualTo("[0]");
        assertThat(saved.getStatus()).isEqualTo("ENABLED");
        assertThat(saved.getTeacherId()).isEqualTo(777L);
        assertThat(saved.getDefaultScore()).isEqualTo(5);
        assertThat(saved.getStem()).isEqualTo("Spring Boot 默认端口？");
        assertThat(response.getStem()).isEqualTo("Spring Boot 默认端口？");
        assertThat(response.getOptions()).containsExactly("8080", "9090");
    }

    @Test
    void updateQuestion_updatesFields() {
        ExamBankQuestionEntity existing = new ExamBankQuestionEntity();
        existing.setId(9L);
        existing.setCourseId(1001L);
        existing.setTeacherId(777L);
        existing.setQuestionType("SINGLE");
        existing.setStem("原文案");
        existing.setOptions("[\"A\"]");
        existing.setAnswer("[0]");
        existing.setDefaultScore(5);
        existing.setStatus("ENABLED");
        when(questionMapper.selectById(9L)).thenReturn(existing);

        ExamQuestionRequest updated = request();
        updated.setStem("更新后的题干");
        updated.setDefaultScore(null);

        ExamBankQuestionResponse response = examBankService.updateQuestion(9L, updated, 777L);

        verify(questionMapper).updateById(existing);
        assertThat(response.getStem()).isEqualTo("更新后的题干");
        assertThat(existing.getOptions()).isEqualTo("[\"8080\",\"9090\"]");
        assertThat(existing.getDefaultScore()).isEqualTo(5);
    }

    @Test
    void deleteQuestion_referencedByPublishedExam_rejected() {
        ExamBankQuestionEntity question = new ExamBankQuestionEntity();
        question.setId(9L);
        question.setTeacherId(777L);
        question.setStatus("ENABLED");
        when(questionMapper.selectById(9L)).thenReturn(question);

        ExamPaperQuestionEntity ref = new ExamPaperQuestionEntity();
        ref.setExamId(55L);
        ref.setQuestionId(9L);
        when(paperQuestionMapper.selectList(any())).thenReturn(List.of(ref));

        ExamEntity published = new ExamEntity();
        published.setId(55L);
        published.setStatus("PUBLISHED");
        when(examMapper.selectById(55L)).thenReturn(published);

        assertThatThrownBy(() -> examBankService.deleteQuestion(9L, 777L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContentErrorCode.EXAM_QUESTION_IN_USE);
        verify(questionMapper, never()).updateById(any(ExamBankQuestionEntity.class));
    }

    @Test
    void deleteQuestion_softDeletesWhenUnreferenced() {
        ExamBankQuestionEntity question = new ExamBankQuestionEntity();
        question.setId(9L);
        question.setTeacherId(777L);
        question.setStatus("ENABLED");
        when(questionMapper.selectById(9L)).thenReturn(question);
        when(paperQuestionMapper.selectList(any())).thenReturn(List.of());

        examBankService.deleteQuestion(9L, 777L);

        assertThat(question.getStatus()).isEqualTo("DISABLED");
        verify(questionMapper).updateById(question);
    }

    @Test
    void listQuestions_filtersByCourseAndEnabled() {
        ExamBankQuestionEntity q1 = new ExamBankQuestionEntity();
        q1.setId(1L);
        q1.setCourseId(1001L);
        q1.setQuestionType("SINGLE");
        q1.setStem("题目一");
        q1.setOptions("[\"A\"]");
        q1.setDefaultScore(5);
        q1.setCreatedAt(LocalDateTime.now());
        when(questionMapper.selectList(any())).thenReturn(List.of(q1));

        List<ExamBankQuestionResponse> result = examBankService.listQuestions(1001L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStem()).isEqualTo("题目一");
        verify(questionMapper).selectList(any());
    }
}
