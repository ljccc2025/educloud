package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.request.ExamCreateRequest;
import com.educloud.content.dto.response.ExamResponse;
import com.educloud.content.entity.ExamAttemptEntity;
import com.educloud.content.entity.ExamBankQuestionEntity;
import com.educloud.content.entity.ExamEntity;
import com.educloud.content.entity.ExamPaperQuestionEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.ExamAttemptMapper;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.registerTableInfo(
                ExamBankQuestionEntity.class, ExamEntity.class, ExamPaperQuestionEntity.class,
                ExamAttemptEntity.class);
    }

    @Mock
    private ExamMapper examMapper;
    @Mock
    private ExamPaperQuestionMapper paperQuestionMapper;
    @Mock
    private ExamAttemptMapper attemptMapper;
    @Mock
    private ExamBankQuestionMapper bankQuestionMapper;
    @Mock
    private CourseClient courseClient;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ExamService examService;

    private static ExamCreateRequest createRequest() {
        ExamCreateRequest req = new ExamCreateRequest();
        req.setCourseId(1001L);
        req.setTitle("期中考试");
        req.setDescription("期中测试");
        req.setDurationMinutes(60);
        req.setPassScore(30);
        req.setStartTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        req.setEndTime(LocalDateTime.of(2026, 6, 1, 12, 0));

        ExamCreateRequest.PaperItem item1 = new ExamCreateRequest.PaperItem();
        item1.setQuestionId(1L);
        item1.setScore(10);
        ExamCreateRequest.PaperItem item2 = new ExamCreateRequest.PaperItem();
        item2.setQuestionId(2L);
        item2.setScore(20);
        req.setPaper(List.of(item1, item2));
        return req;
    }

    private static ExamBankQuestionEntity bankQuestion(Long id, String stem) {
        ExamBankQuestionEntity q = new ExamBankQuestionEntity();
        q.setId(id);
        q.setCourseId(1001L);
        q.setTeacherId(777L);
        q.setQuestionType("SINGLE");
        q.setStem(stem);
        q.setOptions("[\"8080\",\"9090\"]");
        q.setAnswer("[0]");
        q.setStatus("ENABLED");
        return q;
    }

    private static ExamEntity draftExam(Long id) {
        ExamEntity exam = new ExamEntity();
        exam.setId(id);
        exam.setCourseId(1001L);
        exam.setTeacherId(777L);
        exam.setCourseTitle("Spring Boot 微服务实践");
        exam.setTitle("期中考试");
        exam.setDurationMinutes(60);
        exam.setPassScore(30);
        exam.setStartTime(LocalDateTime.now().minusHours(1));
        exam.setEndTime(LocalDateTime.now().plusHours(1));
        exam.setStatus("DRAFT");
        return exam;
    }

    @Test
    void createExam_buildsSnapshotAndTotals() {
        stubExamIdOnInsert(11L);
        when(bankQuestionMapper.selectList(any())).thenReturn(List.of(
                bankQuestion(1L, "Spring Boot 默认端口？"),
                bankQuestion(2L, "MyBatis-Plus 的父类？")));
        when(paperQuestionMapper.selectList(any())).thenReturn(List.of());

        ArgumentCaptor<ExamPaperQuestionEntity> captor = ArgumentCaptor.forClass(ExamPaperQuestionEntity.class);
        ArgumentCaptor<ExamEntity> examCaptor = ArgumentCaptor.forClass(ExamEntity.class);

        ExamResponse response = examService.createExam(createRequest(), 777L);

        verify(paperQuestionMapper, times(2)).insert(captor.capture());
        List<ExamPaperQuestionEntity> rows = captor.getAllValues();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getQuestionSnapshot())
                .contains("Spring Boot 默认端口？")
                .contains("\"answer\":[0]")
                .contains("\"score\":10");
        assertThat(rows.get(1).getQuestionSnapshot())
                .contains("MyBatis-Plus 的父类？")
                .contains("\"score\":20");
        assertThat(rows).extracting(ExamPaperQuestionEntity::getQuestionId).containsExactly(1L, 2L);
        assertThat(response.getTotalScore()).isEqualTo(30);
        verify(examMapper).insert(examCaptor.capture());
        assertThat(examCaptor.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(examCaptor.getValue().getTeacherId()).isEqualTo(777L);
    }

    @Test
    void createExam_snapshotContract_keysAreStable() throws Exception {
        stubExamIdOnInsert(11L);
        when(bankQuestionMapper.selectList(any())).thenReturn(List.of(
                bankQuestion(1L, "Spring Boot 默认端口？"),
                bankQuestion(2L, "MyBatis-Plus 的父类？")));
        when(paperQuestionMapper.selectList(any())).thenReturn(List.of());

        ArgumentCaptor<ExamPaperQuestionEntity> captor = ArgumentCaptor.forClass(ExamPaperQuestionEntity.class);

        examService.createExam(createRequest(), 777L);

        verify(paperQuestionMapper, times(2)).insert(captor.capture());
        String snapshotJson = captor.getAllValues().get(0).getQuestionSnapshot();
        Map<String, Object> snapshot = objectMapper.readValue(
                snapshotJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        // 快照契约固定：六 key 缺一不可，防未来 key 改名
        assertThat(snapshot.keySet()).containsExactlyInAnyOrder(
                "questionId", "questionType", "stem", "options", "answer", "score");
    }

    @Test
    void publish_rejectsEmptyPaper() {
        when(examMapper.selectById(11L)).thenReturn(draftExam(11L));
        when(paperQuestionMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> examService.publishExam(11L, 777L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContentErrorCode.EXAM_PAPER_EMPTY);
    }

    @Test
    void publish_setsPublished() {
        ExamEntity exam = draftExam(11L);
        when(examMapper.selectById(11L)).thenReturn(exam);
        when(paperQuestionMapper.selectCount(any())).thenReturn(1L);

        examService.publishExam(11L, 777L);

        ArgumentCaptor<ExamEntity> captor = ArgumentCaptor.forClass(ExamEntity.class);
        verify(examMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void update_rejectsPublishedExam() {
        ExamEntity published = draftExam(11L);
        published.setStatus("PUBLISHED");
        when(examMapper.selectById(11L)).thenReturn(published);

        assertThatThrownBy(() -> examService.updateExam(11L, createRequest(), 777L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContentErrorCode.EXAM_NOT_DRAFT);
    }

    @Test
    void createExam_rejectsMissingQuestion() {
        when(bankQuestionMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> examService.createExam(createRequest(), 777L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContentErrorCode.EXAM_QUESTION_NOT_FOUND);
    }

    @Test
    void studentView_whenGraded_omitsQuestionsButReportsQuestionCount() {
        ExamEntity published = draftExam(11L);
        published.setStatus("PUBLISHED");
        ExamAttemptEntity graded = new ExamAttemptEntity();
        graded.setStatus("GRADED");
        graded.setScore(40);
        graded.setPassed(1);
        when(examMapper.selectById(11L)).thenReturn(published);
        when(courseClient.isEnrolled(1001L, 900L)).thenReturn(true);
        when(attemptMapper.selectOne(any())).thenReturn(graded);
        when(paperQuestionMapper.selectList(any())).thenReturn(List.of(
                paperRow(1L, 11L, 0), paperRow(2L, 11L, 1), paperRow(3L, 11L, 2)));

        ExamResponse view = examService.getStudentExam(11L, 900L);

        assertThat(view.getQuestions()).isEmpty();
        assertThat(view.getQuestionCount()).isEqualTo(3);
        assertThat(view.getScore()).isEqualTo(40);
        assertThat(view.getPassed()).isTrue();
    }

    @Test
    void listStudentExams_loadsAttemptsAndPaperInOneQueryEach() {
        ExamEntity gradedExam = draftExam(11L);
        gradedExam.setStatus("PUBLISHED");
        ExamEntity openExam = draftExam(12L);
        openExam.setStatus("PUBLISHED");
        when(examMapper.selectList(any())).thenReturn(List.of(gradedExam, openExam));
        when(courseClient.isEnrolled(1001L, 900L)).thenReturn(true);
        ExamAttemptEntity graded = new ExamAttemptEntity();
        graded.setExamId(11L);
        graded.setStatus("GRADED");
        graded.setScore(40);
        graded.setPassed(1);
        when(attemptMapper.selectList(any())).thenReturn(List.of(graded));
        when(paperQuestionMapper.selectList(any())).thenReturn(List.of(
                paperRow(1L, 11L, 0), paperRow(2L, 11L, 1), paperRow(3L, 12L, 0)));

        List<ExamResponse> views = examService.listStudentExams(900L);

        assertThat(views).hasSize(2);
        // 已批改：不下发题目但题数正确；未批改：正常下发题目
        assertThat(views.get(0).getQuestions()).isEmpty();
        assertThat(views.get(0).getQuestionCount()).isEqualTo(2);
        assertThat(views.get(1).getQuestions()).hasSize(1);
        assertThat(views.get(1).getQuestionCount()).isEqualTo(1);
        // 列表路径不得逐考试发起查询
        verify(paperQuestionMapper, times(1)).selectList(any());
        verify(attemptMapper, times(1)).selectList(any());
        verify(attemptMapper, never()).selectOne(any());
        // 报名校验按课程去重，两门同课程考试只查一次
        verify(courseClient, times(1)).isEnrolled(1001L, 900L);
    }

    @Test
    void listStudentExams_excludesExamsOfCoursesNotEnrolled() {
        ExamEntity other = draftExam(11L);
        other.setStatus("PUBLISHED");
        when(examMapper.selectList(any())).thenReturn(List.of(other));
        when(courseClient.isEnrolled(1001L, 900L)).thenReturn(false);

        assertThat(examService.listStudentExams(900L)).isEmpty();
    }

    /** 模拟生产行为：MyBatis-Plus 在 insert 时回填雪花 id。 */
    private void stubExamIdOnInsert(long id) {
        doAnswer(invocation -> {
            ((ExamEntity) invocation.getArgument(0)).setId(id);
            return 1;
        }).when(examMapper).insert(any(ExamEntity.class));
    }

    private static ExamPaperQuestionEntity paperRow(Long id, Long examId, int sortOrder) {
        ExamPaperQuestionEntity row = new ExamPaperQuestionEntity();
        row.setId(id);
        row.setExamId(examId);
        row.setQuestionId(1L);
        row.setScore(10);
        row.setSortOrder(sortOrder);
        row.setQuestionSnapshot("{\"questionId\":1,\"questionType\":\"SINGLE\",\"stem\":\"题干\","
                + "\"options\":[\"A\",\"B\"],\"answer\":[0],\"score\":10}");
        return row;
    }

    private static org.mockito.verification.VerificationMode verifyTimes(int times) {
        return org.mockito.Mockito.times(times);
    }
}
