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
import com.educloud.content.exam.ExamGradingEngine.GradedQuestion;
import com.educloud.content.exam.ExamGradingEngine.GradeResult;
import com.educloud.content.exam.ExamQuestionSnapshot;
import com.educloud.content.mapper.ExamAttemptMapper;
import com.educloud.content.mapper.ExamMapper;
import com.educloud.content.mapper.ExamPaperQuestionMapper;
import com.educloud.content.messaging.ContentEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamAttemptService {

    /** 切屏次数 >= 该阈值标记 flagged。 */
    private static final int FLAG_THRESHOLD = 5;
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_PUBLISHED = "PUBLISHED";

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
            if (STATUS_IN_PROGRESS.equals(existing.getStatus())) {
                // 幂等复用：刷新页面/重复点击开始考试时返回进行中的 attempt，避免前端误判为不可考。
                return existing;
            }
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_ALREADY_EXISTS,
                    "Exam already attempted: exam=" + examId + ", student=" + studentId);
        }
        ExamAttemptEntity attempt = new ExamAttemptEntity();
        attempt.setExamId(examId);
        attempt.setStudentId(studentId);
        attempt.setStatus(STATUS_IN_PROGRESS);
        attempt.setStartedAt(LocalDateTime.now());
        attempt.setTabSwitchCount(0);
        attempt.setFlagged(0);
        attempt.setTimeout(0);
        try {
            attemptMapper.insert(attempt);
        } catch (DuplicateKeyException duplicate) {
            // uk_exam_student(exam_id, student_id) 唯一约束并发兜底：另一请求已抢先创建。
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_ALREADY_EXISTS,
                    "Active attempt already exists: exam=" + examId + ", student=" + studentId);
        }
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
        if (!attempt.getExamId().equals(examId)) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_FOUND,
                    "Attempt " + attemptId + " does not belong to exam " + examId);
        }
        if (!STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_SUBMITTABLE,
                    "Attempt is not in progress: " + attemptId);
        }

        ExamEntity exam = requirePublishedInWindow(examId);
        List<ExamQuestionSnapshot> paper = loadPaper(examId);
        Map<Long, List<Integer>> answers = request.getAnswers() == null ? Map.of() : request.getAnswers();
        boolean timeout = isTimeout(exam, attempt);
        int tabSwitches = request.getTabSwitchCount() == null ? 0 : request.getTabSwitchCount();
        boolean passed = ExamGradingEngine.grade(paper, answers).earnedScore() >= exam.getPassScore();

        attempt.setTabSwitchCount(tabSwitches);
        attempt.setFlagged(tabSwitches >= FLAG_THRESHOLD ? 1 : 0);

        ExamAttemptResponse response = doGrade(attempt, paper, answers, passed, timeout);

        int earned = attempt.getScore();
        try {
            contentEventPublisher.examGraded(
                    exam.getId(), exam.getTitle(), exam.getCourseId(), exam.getCourseTitle(),
                    studentId, earned, earned >= exam.getPassScore(), 1L, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Failed to publish ExamGraded event for exam {} student {}",
                    examId, studentId, e);
        }
        return response;
    }

    public ExamAttemptResponse getAttemptResult(Long examId, Long attemptId, Long studentId) {
        ExamAttemptEntity attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_FOUND,
                    "Exam attempt not found: " + attemptId);
        }
        if (!attempt.getStudentId().equals(studentId)) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_OWNED,
                    "Attempt " + attemptId + " does not belong to student " + studentId);
        }
        if (!attempt.getExamId().equals(examId)) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_FOUND,
                    "Attempt " + attemptId + " does not belong to exam " + examId);
        }
        if (!"GRADED".equals(attempt.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_SUBMITTABLE,
                    "Attempt not graded yet: " + attemptId);
        }
        Map<Long, List<Integer>> answers = readAnswers(attempt.getAnswersJson());
        List<ExamQuestionSnapshot> paper = loadPaper(examId);
        ExamGradingEngine.GradeResult result = ExamGradingEngine.grade(paper, answers);
        return toAttemptResponse(attempt, paper, answers, result, attempt.getPassed() == 1);
    }

    private Map<Long, List<Integer>> readAnswers(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse answers_json for attempt, treat as empty", e);
            return Map.of();
        }
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

        ExamAttemptResponse response;
        try {
            response = doGrade(attempt, paper, answers, false, true);
        } catch (BusinessException e) {
            // CAS 抢占失败（updated != 1）：另一请求已收敛判分，返回 null 而非抛错。
            return null;
        }

        int earned = attempt.getScore();
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
        return response;
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
        if (!STATUS_PUBLISHED.equals(exam.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_PUBLISHED, "Exam is not published: " + examId);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime()) || now.isAfter(exam.getEndTime())) {
            throw new BusinessException(ContentErrorCode.EXAM_OUTSIDE_WINDOW,
                    "Exam outside window: " + examId);
        }
        return exam;
    }

    /**
     * 判分收尾公共路径：调用判分引擎、落库（CAS）、构建响应。tabSwitchCount/flagged 由调用方
     * 在进入本方法前设置（submitAttempt 重算，timeoutSubmit 保持原值），本方法不会覆盖。
     */
    private ExamAttemptResponse doGrade(ExamAttemptEntity attempt, List<ExamQuestionSnapshot> paper,
                                        Map<Long, List<Integer>> answers, boolean passed, boolean timeout) {
        ExamGradingEngine.GradeResult result = ExamGradingEngine.grade(paper, answers);
        int earned = result.earnedScore();
        attempt.setScore(earned);
        attempt.setPassed(passed ? 1 : 0);
        attempt.setAnswersJson(writeJson(answers));
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setTimeout(timeout ? 1 : 0);
        int updated = attemptMapper.markGraded(attempt);
        if (updated != 1) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_SUBMITTABLE,
                    "Attempt was concurrently submitted: " + attempt.getId());
        }
        return toAttemptResponse(attempt, paper, answers, result, passed);
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
                                                  GradeResult result,
                                                  boolean passed) {
        Map<Long, GradedQuestion> gradedById = result.details().stream()
                .collect(Collectors.toMap(GradedQuestion::questionId, Function.identity()));
        List<ExamAttemptResponse.ExamQuestionResult> results = new ArrayList<>();
        for (ExamQuestionSnapshot q : paper) {
            GradedQuestion g = gradedById.get(q.questionId());
            results.add(ExamAttemptResponse.ExamQuestionResult.builder()
                    .questionId(q.questionId())
                    .questionType(g.questionType())
                    .options(q.options())
                    .answer(q.answer())
                    .score(g.score())
                    .correct(g.correct())
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
}
