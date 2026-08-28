package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.request.ExamSubmitRequest;
import com.educloud.content.dto.response.ExamAttemptResponse;
import com.educloud.content.entity.ExamAttemptEntity;
import com.educloud.content.entity.ExamEntity;
import com.educloud.content.entity.ExamPaperQuestionEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.exam.ExamGradingEngine;
import com.educloud.content.exam.ExamQuestionSnapshot;
import com.educloud.content.mapper.ExamAttemptMapper;
import com.educloud.content.mapper.ExamMapper;
import com.educloud.content.mapper.ExamPaperQuestionMapper;
import com.educloud.content.messaging.ContentEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamAttemptService {

    /** 切屏次数 >= 该阈值标记 flagged。 */
    private static final int FLAG_THRESHOLD = 5;
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private final ExamMapper examMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamAttemptMapper attemptMapper;
    private final ContentEventPublisher contentEventPublisher;
    private final ObjectMapper objectMapper;

    public ExamAttemptEntity startAttempt(Long examId, Long studentId) {
        ExamEntity exam = requirePublishedInWindow(examId);
        ExamAttemptEntity existing = attemptMapper.selectOne(
                new LambdaQueryWrapper<ExamAttemptEntity>()
                        .eq(ExamAttemptEntity::getExamId, examId)
                        .eq(ExamAttemptEntity::getStudentId, studentId));
        if (existing != null) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_ALREADY_EXISTS,
                    "Active attempt already exists: exam=" + examId + ", student=" + studentId);
        }
        ExamAttemptEntity attempt = new ExamAttemptEntity();
        attempt.setExamId(examId);
        attempt.setStudentId(studentId);
        attempt.setStatus(STATUS_IN_PROGRESS);
        attempt.setStartedAt(LocalDateTime.now());
        attempt.setTabSwitchCount(0);
        attempt.setFlagged(0);
        attempt.setTimeout(0);
        attemptMapper.insert(attempt);
        return attempt;
    }

    @Transactional
    public ExamAttemptResponse submitAttempt(Long examId, Long attemptId, Long studentId, ExamSubmitRequest request) {
        ExamAttemptEntity attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_FOUND,
                    "Exam attempt not found: " + attemptId);
        }
        if (!attempt.getStudentId().equals(studentId)) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_OWNED,
                    "Attempt " + attemptId + " does not belong to student " + studentId);
        }
        if (!STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_SUBMITTABLE,
                    "Attempt is not in progress: " + attemptId);
        }

        ExamEntity exam = requirePublishedInWindow(examId);
        List<ExamQuestionSnapshot> paper = loadPaper(examId);
        Map<Long, List<Integer>> answers = request.getAnswers() == null ? Map.of() : request.getAnswers();
        boolean timeout = isTimeout(exam, attempt);
        ExamGradingEngine.GradeResult result = ExamGradingEngine.grade(paper, answers);
        int earned = result.earnedScore();
        int tabSwitches = request.getTabSwitchCount() == null ? 0 : request.getTabSwitchCount();

        attempt.setScore(earned);
        attempt.setPassed(earned >= exam.getPassScore() ? 1 : 0);
        attempt.setAnswersJson(writeJson(answers));
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setTabSwitchCount(tabSwitches);
        attempt.setFlagged(tabSwitches >= FLAG_THRESHOLD ? 1 : 0);
        attempt.setTimeout(timeout ? 1 : 0);

        int updated = attemptMapper.markGraded(attempt);
        if (updated != 1) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_SUBMITTABLE,
                    "Attempt was concurrently submitted: " + attemptId);
        }

        try {
            contentEventPublisher.examGraded(
                    exam.getId(), exam.getTitle(), exam.getCourseId(), exam.getCourseTitle(),
                    studentId, earned, earned >= exam.getPassScore(), 1L, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Failed to publish ExamGraded event for exam {} student {}",
                    examId, studentId, e);
        }
        return toAttemptResponse(attempt, paper, answers, earned >= exam.getPassScore());
    }

    /**
     * 超时收敛判分入口（规格 §5.2）：由 ExamTimeoutSweeper 在考试窗口结束后调用，
     * 不校验考试窗口与上架状态（考试可能已转为 CLOSED），仅校验 attempt 状态与归属。
     *
     * @return 判分后的响应，或 null 表示 attempt 已被并发处理（CAS 抢占失败）。
     */
    public ExamAttemptResponse timeoutSubmit(ExamAttemptEntity attempt) {
        if (attempt == null) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_FOUND, "Exam attempt not found");
        }
        if (!STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_SUBMITTABLE,
                    "Attempt is not in progress: " + attempt.getId());
        }
        List<ExamQuestionSnapshot> paper = loadPaper(attempt.getExamId());
        Map<Long, List<Integer>> answers = Map.of();
        ExamGradingEngine.GradeResult result = ExamGradingEngine.grade(paper, answers);
        int earned = result.earnedScore();
        int tabSwitches = attempt.getTabSwitchCount() == null ? 0 : attempt.getTabSwitchCount();

        attempt.setScore(earned);
        attempt.setPassed(0);
        attempt.setAnswersJson(writeJson(answers));
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setTabSwitchCount(tabSwitches);
        attempt.setFlagged(tabSwitches >= FLAG_THRESHOLD ? 1 : 0);
        attempt.setTimeout(1);

        int updated = attemptMapper.markGraded(attempt);
        if (updated != 1) {
            return null;
        }
        try {
            ExamEntity exam = examMapper.selectById(attempt.getExamId());
            contentEventPublisher.examGraded(
                    exam == null ? attempt.getExamId() : exam.getId(),
                    exam == null ? null : exam.getTitle(),
                    exam == null ? null : exam.getCourseId(),
                    exam == null ? null : exam.getCourseTitle(),
                    attempt.getStudentId(), earned, false, 1L, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Failed to publish ExamGraded event on timeout-sweep: attempt {}",
                    attempt.getId(), e);
        }
        return toAttemptResponse(attempt, paper, answers, false);
    }

    /** 超时判定：服务端时间 > started_at + duration。 */
    private boolean isTimeout(ExamEntity exam, ExamAttemptEntity attempt) {
        LocalDateTime deadline = attempt.getStartedAt().plusMinutes(exam.getDurationMinutes());
        return LocalDateTime.now().isAfter(deadline);
    }

    public List<ExamQuestionSnapshot> loadPaper(Long examId) {
        List<ExamPaperQuestionEntity> rows = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestionEntity>()
                        .eq(ExamPaperQuestionEntity::getExamId, examId)
                        .orderByAsc(ExamPaperQuestionEntity::getSortOrder));
        List<ExamQuestionSnapshot> paper = new ArrayList<>();
        for (ExamPaperQuestionEntity row : rows) {
            paper.add(readSnapshot(row));
        }
        return paper;
    }

    private ExamQuestionSnapshot readSnapshot(ExamPaperQuestionEntity row) {
        try {
            Map<String, Object> snap = objectMapper.readValue(row.getQuestionSnapshot(),
                    new TypeReference<>() {
                    });
            Long questionId = row.getQuestionId();
            String type = String.valueOf(snap.get("questionType"));
            List<String> options = objectMapper.convertValue(snap.get("options"), new TypeReference<>() {
            });
            List<Integer> answer = objectMapper.convertValue(snap.get("answer"), new TypeReference<>() {
            });
            int score = row.getScore();
            return new ExamQuestionSnapshot(questionId, type, options, answer, score);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid question snapshot for paper row " + row.getId(), e);
        }
    }

    private ExamEntity requirePublishedInWindow(Long examId) {
        ExamEntity exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_FOUND, "Exam not found: " + examId);
        }
        if (!"PUBLISHED".equals(exam.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_PUBLISHED, "Exam is not published: " + examId);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime()) || now.isAfter(exam.getEndTime())) {
            throw new BusinessException(ContentErrorCode.EXAM_OUTSIDE_WINDOW,
                    "Exam outside window: " + examId);
        }
        return exam;
    }

    private String writeJson(Map<Long, List<Integer>> answers) {
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize answers", e);
        }
    }

    private ExamAttemptResponse toAttemptResponse(ExamAttemptEntity attempt,
                                                  List<ExamQuestionSnapshot> paper,
                                                  Map<Long, List<Integer>> answers,
                                                  boolean passed) {
        List<ExamAttemptResponse.ExamQuestionResult> results = new ArrayList<>();
        for (ExamQuestionSnapshot q : paper) {
            results.add(ExamAttemptResponse.ExamQuestionResult.builder()
                    .questionId(q.questionId())
                    .questionType(q.questionType())
                    .options(q.options())
                    .answer(q.answer())
                    .score(q.score())
                    .correct(answers.containsKey(q.questionId())
                            && isCorrectAnswer(q, answers.get(q.questionId())))
                    .build());
        }
        return ExamAttemptResponse.builder()
                .id(attempt.getId())
                .examId(attempt.getExamId())
                .status(attempt.getStatus())
                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())
                .score(attempt.getScore())
                .passed(passed)
                .timeout(attempt.getTimeout() != null && attempt.getTimeout() == 1)
                .tabSwitchCount(attempt.getTabSwitchCount())
                .answers(answers)
                .results(results)
                .build();
    }

    private boolean isCorrectAnswer(ExamQuestionSnapshot q, List<Integer> chosen) {
        if ("MULTIPLE".equals(q.questionType())) {
            return chosen.size() == q.answer().size()
                    && chosen.stream().sorted().toList().equals(q.answer().stream().sorted().toList());
        }
        return chosen.size() == 1 && chosen.get(0).equals(q.answer().get(0));
    }
}
