package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.request.ExamSubmitRequest;
import com.educloud.content.entity.ExamAttemptEntity;
import com.educloud.content.entity.ExamEntity;
import com.educloud.content.entity.ExamPaperQuestionEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.ExamAttemptMapper;
import com.educloud.content.mapper.ExamMapper;
import com.educloud.content.mapper.ExamPaperQuestionMapper;
import com.educloud.content.messaging.ContentEventPublisher;
import com.educloud.content.support.MybatisPlusTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamAttemptServiceTest {

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.registerTableInfo(ExamAttemptEntity.class, ExamEntity.class, ExamPaperQuestionEntity.class);
    }

    @Mock
    private ExamMapper examMapper;
    @Mock
    private ExamPaperQuestionMapper paperQuestionMapper;
    @Mock
    private ExamAttemptMapper attemptMapper;
    @Mock
    private ContentEventPublisher eventPublisher;
    @Mock
    private CourseClient courseClient;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ExamAttemptService attemptService;

    private static ExamEntity publishedExam() {
        ExamEntity exam = new ExamEntity();
        exam.setId(101L);
        exam.setCourseId(1001L);
        exam.setCourseTitle("Spring Boot 微服务实践");
        exam.setTitle("期中考试");
        exam.setDurationMinutes(60);
        exam.setPassScore(30);
        exam.setStartTime(LocalDateTime.now().minusHours(1));
        exam.setEndTime(LocalDateTime.now().plusHours(1));
        exam.setStatus("PUBLISHED");
        return exam;
    }

    @Test
    void startAttempt_rejectsUnpublishedExam() {
        ExamEntity draft = publishedExam();
        draft.setStatus("DRAFT");
        when(examMapper.selectById(101L)).thenReturn(draft);

        assertThatThrownBy(() -> attemptService.startAttempt(101L, 2001L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ContentErrorCode.EXAM_NOT_PUBLISHED);
    }

    @Test
    void startAttempt_rejectsOutsideWindow() {
        ExamEntity exam = publishedExam();
        exam.setStartTime(LocalDateTime.now().plusDays(1));
        when(examMapper.selectById(101L)).thenReturn(exam);

        assertThatThrownBy(() -> attemptService.startAttempt(101L, 2001L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ContentErrorCode.EXAM_OUTSIDE_WINDOW);
    }

    @Test
    void startAttempt_rejectsWhenNotEnrolled() {
        when(examMapper.selectById(101L)).thenReturn(publishedExam());
        when(courseClient.isEnrolled(1001L, 2001L)).thenReturn(false);

        assertThatThrownBy(() -> attemptService.startAttempt(101L, 2001L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ContentErrorCode.EXAM_NOT_ENROLLED);
    }

    @Test
    void startAttempt_reusesInProgressAttempt() {
        when(examMapper.selectById(101L)).thenReturn(publishedExam());
        when(courseClient.isEnrolled(1001L, 2001L)).thenReturn(true);
        ExamAttemptEntity inProgress = new ExamAttemptEntity();
        inProgress.setId(501L);
        inProgress.setExamId(101L);
        inProgress.setStudentId(2001L);
        inProgress.setStatus("IN_PROGRESS");
        inProgress.setStartedAt(LocalDateTime.now().minusMinutes(5));
        when(attemptMapper.selectOne(any())).thenReturn(inProgress);

        ExamAttemptEntity result = attemptService.startAttempt(101L, 2001L);

        assertThat(result.getId()).isEqualTo(501L);
        assertThat(result.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void startAttempt_rejectsGradedAttempt() {
        when(examMapper.selectById(101L)).thenReturn(publishedExam());
        when(courseClient.isEnrolled(1001L, 2001L)).thenReturn(true);
        ExamAttemptEntity graded = new ExamAttemptEntity();
        graded.setStatus("GRADED");
        when(attemptMapper.selectOne(any())).thenReturn(graded);

        assertThatThrownBy(() -> attemptService.startAttempt(101L, 2001L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ContentErrorCode.EXAM_ATTEMPT_ALREADY_EXISTS);
    }

    @Test
    void submit_gradesAndPublishesEvent() {
        when(examMapper.selectById(101L)).thenReturn(publishedExam());
        when(paperQuestionMapper.selectList(any())).thenReturn(List.of(
                paper(1L, "SINGLE", List.of(0), 10),
                paper(2L, "MULTIPLE", List.of(0, 2), 20)));
        ExamAttemptEntity attempt = new ExamAttemptEntity();
        attempt.setId(501L);
        attempt.setExamId(101L);
        attempt.setStudentId(2001L);
        attempt.setStatus("IN_PROGRESS");
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(10));
        when(attemptMapper.selectById(501L)).thenReturn(attempt);
        when(attemptMapper.markGraded(any())).thenReturn(1);

        ExamSubmitRequest request = new ExamSubmitRequest();
        request.setAnswers(Map.of(1L, List.of(0), 2L, List.of(0, 2)));
        request.setTabSwitchCount(2);

        var response = attemptService.submitAttempt(101L, 501L, 2001L, request);

        assertThat(response.getScore()).isEqualTo(30);
        assertThat(response.getPassed()).isTrue();
        verify(attemptMapper).markGraded(any());
        verify(eventPublisher).examGraded(any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong(), any());
    }

    @Test
    void submit_rejectsAttemptNotOwned() {
        when(attemptMapper.selectById(501L)).thenReturn(attempt(501L, 9999L));

        assertThatThrownBy(() -> attemptService.submitAttempt(101L, 501L, 2001L, new ExamSubmitRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ContentErrorCode.EXAM_ATTEMPT_NOT_OWNED);
        verify(attemptMapper, never()).markGraded(any());
    }

    @Test
    void submit_rejectsMissingAttempt() {
        when(attemptMapper.selectById(501L)).thenReturn(null);

        assertThatThrownBy(() -> attemptService.submitAttempt(101L, 501L, 2001L, new ExamSubmitRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ContentErrorCode.EXAM_ATTEMPT_NOT_FOUND);
        verify(attemptMapper, never()).markGraded(any());
    }

    @Test
    void submit_rejectsAlreadyGradedAttempt() {
        ExamAttemptEntity graded = attempt(501L, 2001L);
        graded.setStatus("GRADED");
        when(attemptMapper.selectById(501L)).thenReturn(graded);

        assertThatThrownBy(() -> attemptService.submitAttempt(101L, 501L, 2001L, new ExamSubmitRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ContentErrorCode.EXAM_ATTEMPT_NOT_SUBMITTABLE);
        verify(attemptMapper, never()).markGraded(any());
    }

    @Test
    void submit_rejectsExamIdMismatch() {
        ExamAttemptEntity mismatch = attempt(501L, 2001L);
        mismatch.setExamId(999L);
        when(attemptMapper.selectById(501L)).thenReturn(mismatch);

        assertThatThrownBy(() -> attemptService.submitAttempt(101L, 501L, 2001L, new ExamSubmitRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ContentErrorCode.EXAM_ATTEMPT_NOT_FOUND);
        verify(attemptMapper, never()).markGraded(any());
    }

    @Test
    void timeoutSubmit_gradesWithTimeoutFlag() {
        when(paperQuestionMapper.selectList(any())).thenReturn(List.of(
                paper(1L, "SINGLE", List.of(0), 10),
                paper(2L, "MULTIPLE", List.of(0, 2), 20)));
        when(attemptMapper.markGraded(any())).thenReturn(1);
        when(examMapper.selectById(101L)).thenReturn(publishedExam());

        ExamAttemptEntity timedOut = attempt(501L, 2001L);
        timedOut.setStartedAt(LocalDateTime.now().minusMinutes(120));
        timedOut.setTabSwitchCount(7);

        var response = attemptService.timeoutSubmit(timedOut);

        // 超时：无答案判分，score=0、passed=false、timeout=true；tabSwitchCount 保持原值。
        assertThat(response).isNotNull();
        assertThat(response.getScore()).isZero();
        assertThat(response.getPassed()).isFalse();
        assertThat(response.getTimeout()).isTrue();
        assertThat(response.getTabSwitchCount()).isEqualTo(7);
        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getResults()).allMatch(r -> !Boolean.TRUE.equals(r.getCorrect()));
        verify(attemptMapper).markGraded(any());
        verify(eventPublisher).examGraded(any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong(), any());
    }

    @Test
    void timeoutSubmit_returnsNullWhenCASLost() {
        when(paperQuestionMapper.selectList(any())).thenReturn(List.of(
                paper(1L, "SINGLE", List.of(0), 10)));
        when(attemptMapper.markGraded(any())).thenReturn(0);

        ExamAttemptEntity timedOut = attempt(501L, 2001L);
        timedOut.setStartedAt(LocalDateTime.now().minusMinutes(120));

        assertThat(attemptService.timeoutSubmit(timedOut)).isNull();
        verify(eventPublisher, never()).examGraded(any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong(), any());
    }

    private static ExamPaperQuestionEntity paper(Long id, String type, List<Integer> answer, int score) {
        ExamPaperQuestionEntity p = new ExamPaperQuestionEntity();
        p.setId(id);
        p.setExamId(101L);
        p.setQuestionId(id);
        p.setScore(score);
        p.setQuestionSnapshot("{\"questionId\":" + id + ",\"questionType\":\"" + type
                + "\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"answer\":" + answer + ",\"score\":" + score + "}");
        return p;
    }

    private static ExamAttemptEntity attempt(Long id, Long studentId) {
        ExamAttemptEntity a = new ExamAttemptEntity();
        a.setId(id);
        a.setExamId(101L);
        a.setStudentId(studentId);
        a.setStatus("IN_PROGRESS");
        a.setStartedAt(LocalDateTime.now().minusMinutes(5));
        return a;
    }
}
