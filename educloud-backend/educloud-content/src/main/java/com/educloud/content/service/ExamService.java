package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.request.ExamCreateRequest;
import com.educloud.content.dto.response.ExamQuestionResponse;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExamService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_GRADED = "GRADED";
    private static final String STATUS_ENABLED = "ENABLED";

    private final ExamMapper examMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamAttemptMapper attemptMapper;
    private final ExamBankQuestionMapper bankQuestionMapper;
    private final ObjectMapper objectMapper;
    private final CourseClient courseClient;

    public List<ExamResponse> listStudentExams(Long studentId) {
        List<ExamEntity> exams = examMapper.selectList(
                new LambdaQueryWrapper<ExamEntity>()
                        .eq(ExamEntity::getStatus, STATUS_PUBLISHED)
                        .orderByDesc(ExamEntity::getStartTime));
        List<ExamResponse> result = new ArrayList<>();
        for (ExamEntity exam : exams) {
            ExamAttemptEntity attempt = attemptMapper.selectOne(
                    new LambdaQueryWrapper<ExamAttemptEntity>()
                            .eq(ExamAttemptEntity::getExamId, exam.getId())
                            .eq(ExamAttemptEntity::getStudentId, studentId));
            result.add(toStudentView(exam, attempt));
        }
        return result;
    }

    public ExamResponse getStudentExam(Long examId, Long studentId) {
        ExamEntity exam = requireExam(examId);
        if (!STATUS_PUBLISHED.equals(exam.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_PUBLISHED,
                    "Exam is not published: " + examId);
        }
        ExamAttemptEntity attempt = attemptMapper.selectOne(
                new LambdaQueryWrapper<ExamAttemptEntity>()
                        .eq(ExamAttemptEntity::getExamId, examId)
                        .eq(ExamAttemptEntity::getStudentId, studentId));
        return toStudentView(exam, attempt);
    }

    @Transactional
    public ExamResponse createExam(ExamCreateRequest request, Long teacherId) {
        validateWindow(request.getStartTime(), request.getEndTime());
        List<ExamBankQuestionEntity> questions = loadQuestions(request.getPaper());
        if (questions.size() != request.getPaper().size()) {
            throw new BusinessException(ContentErrorCode.EXAM_QUESTION_NOT_FOUND,
                    "Some paper questions not found or disabled");
        }
        int totalScore = request.getPaper().stream()
                .mapToInt(ExamCreateRequest.PaperItem::getScore).sum();

        ExamEntity exam = new ExamEntity();
        exam.setCourseId(request.getCourseId());
        exam.setCourseTitle(resolveCourseTitle(request.getCourseId()));
        exam.setTitle(request.getTitle());
        exam.setDescription(request.getDescription());
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setTotalScore(totalScore);
        exam.setPassScore(request.getPassScore());
        exam.setStartTime(request.getStartTime());
        exam.setEndTime(request.getEndTime());
        exam.setStatus(STATUS_DRAFT);
        exam.setTeacherId(teacherId);
        examMapper.insert(exam);

        applyPaper(exam.getId(), request.getPaper(), questions);
        return toStudentView(exam, null);
    }

    public void publishExam(Long examId, Long teacherId) {
        ExamEntity exam = requireExam(examId);
        requireTeacherOwnership(exam, teacherId);
        if (!STATUS_DRAFT.equals(exam.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_DRAFT, "Exam is not draft: " + examId);
        }
        Long paperCount = paperQuestionMapper.selectCount(
                new LambdaQueryWrapper<ExamPaperQuestionEntity>()
                        .eq(ExamPaperQuestionEntity::getExamId, examId));
        if (paperCount == null || paperCount == 0) {
            throw new BusinessException(ContentErrorCode.EXAM_PAPER_EMPTY, "Exam paper is empty: " + examId);
        }
        validateWindow(exam.getStartTime(), exam.getEndTime());
        exam.setStatus(STATUS_PUBLISHED);
        examMapper.updateById(exam);
    }

    public List<ExamResponse> listTeacherExams(Long teacherId) {
        return examMapper.selectList(
                        new LambdaQueryWrapper<ExamEntity>()
                                .eq(ExamEntity::getTeacherId, teacherId)
                                .orderByDesc(ExamEntity::getCreatedAt))
                .stream().map(e -> toStudentView(e, null)).toList();
    }

    @Transactional
    public ExamResponse updateExam(Long examId, ExamCreateRequest request, Long teacherId) {
        ExamEntity exam = requireExam(examId);
        requireTeacherOwnership(exam, teacherId);
        if (!STATUS_DRAFT.equals(exam.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_DRAFT, "Exam is not draft: " + examId);
        }
        validateWindow(request.getStartTime(), request.getEndTime());
        List<ExamBankQuestionEntity> questions = loadQuestions(request.getPaper());
        if (questions.size() != request.getPaper().size()) {
            throw new BusinessException(ContentErrorCode.EXAM_QUESTION_NOT_FOUND,
                    "Some paper questions not found or disabled");
        }
        paperQuestionMapper.delete(
                new LambdaQueryWrapper<ExamPaperQuestionEntity>()
                        .eq(ExamPaperQuestionEntity::getExamId, examId));
        int totalScore = request.getPaper().stream()
                .mapToInt(ExamCreateRequest.PaperItem::getScore).sum();
        exam.setCourseId(request.getCourseId());
        exam.setCourseTitle(resolveCourseTitle(request.getCourseId()));
        exam.setTitle(request.getTitle());
        exam.setDescription(request.getDescription());
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setTotalScore(totalScore);
        exam.setPassScore(request.getPassScore());
        exam.setStartTime(request.getStartTime());
        exam.setEndTime(request.getEndTime());
        examMapper.updateById(exam);
        applyPaper(examId, request.getPaper(), questions);
        return toStudentView(exam, null);
    }

    public void deleteExam(Long examId, Long teacherId) {
        ExamEntity exam = requireExam(examId);
        requireTeacherOwnership(exam, teacherId);
        if (!STATUS_DRAFT.equals(exam.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_DRAFT, "Exam is not draft: " + examId);
        }
        paperQuestionMapper.delete(
                new LambdaQueryWrapper<ExamPaperQuestionEntity>()
                        .eq(ExamPaperQuestionEntity::getExamId, examId));
        examMapper.deleteById(examId);
    }

    private List<ExamBankQuestionEntity> loadQuestions(List<ExamCreateRequest.PaperItem> paper) {
        List<Long> ids = paper.stream().map(ExamCreateRequest.PaperItem::getQuestionId).toList();
        return bankQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamBankQuestionEntity>()
                        .in(ExamBankQuestionEntity::getId, ids)
                        .eq(ExamBankQuestionEntity::getStatus, STATUS_ENABLED));
    }

    private String buildSnapshot(ExamBankQuestionEntity q, int score) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("questionId", q.getId());
        snap.put("questionType", q.getQuestionType());
        snap.put("stem", q.getStem());
        snap.put("options", readStringList(q.getOptions()));
        snap.put("answer", readIntList(q.getAnswer()));
        snap.put("score", score);
        try {
            return objectMapper.writeValueAsString(snap);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to build snapshot", e);
        }
    }

    private void validateWindow(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BusinessException(ContentErrorCode.EXAM_INVALID_WINDOW,
                    "Invalid exam window: end must be after start");
        }
    }

    private String resolveCourseTitle(Long courseId) {
        try {
            CourseClient.CourseSnapshot snapshot = courseClient.getCourseSnapshot(courseId);
            if (snapshot != null && snapshot.title() != null) {
                return snapshot.title();
            }
        } catch (BusinessException ignored) {
            // 课程服务不可用/解析失败，回退到占位标题
        }
        return "课程 " + courseId;
    }

    /** 组卷公共方法：逐题 buildSnapshot + insert + sortOrder。 */
    private void applyPaper(Long examId, List<ExamCreateRequest.PaperItem> paper,
                            List<ExamBankQuestionEntity> questions) {
        int sort = 0;
        for (ExamCreateRequest.PaperItem item : paper) {
            ExamBankQuestionEntity q = questions.stream()
                    .filter(x -> x.getId().equals(item.getQuestionId()))
                    .findFirst().orElseThrow();
            ExamPaperQuestionEntity row = new ExamPaperQuestionEntity();
            row.setExamId(examId);
            row.setQuestionId(q.getId());
            row.setQuestionSnapshot(buildSnapshot(q, item.getScore()));
            row.setScore(item.getScore());
            row.setSortOrder(sort++);
            paperQuestionMapper.insert(row);
        }
    }

    private void requireTeacherOwnership(ExamEntity exam, Long teacherId) {
        if (!exam.getTeacherId().equals(teacherId)) {
            throw new BusinessException(ContentErrorCode.TEACHER_ACCESS_DENIED,
                    "You are not authorized to manage this exam: " + exam.getId());
        }
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<Integer> readIntList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private ExamResponse toStudentView(ExamEntity exam, ExamAttemptEntity attempt) {
        boolean hasResult = attempt != null && STATUS_GRADED.equals(attempt.getStatus());
        List<ExamQuestionResponse> questions = hasResult
                ? List.of()
                : loadQuestionsForDisplay(exam.getId());
        return ExamResponse.builder()
                .id(exam.getId())
                .courseId(exam.getCourseId())
                .courseTitle(exam.getCourseTitle())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .durationMinutes(exam.getDurationMinutes())
                .totalScore(exam.getTotalScore())
                .passScore(exam.getPassScore())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .status(displayStatus(exam, attempt))
                .questions(questions)
                .score(hasResult ? attempt.getScore() : null)
                .passed(hasResult ? attempt.getPassed() == 1 : null)
                .attemptStatus(attempt == null ? null : attempt.getStatus())
                .build();
    }

    /** 展示状态推导（规格 §5.2）：NOT_STARTED / IN_PROGRESS / GRADED。 */
    private String displayStatus(ExamEntity exam, ExamAttemptEntity attempt) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime())) {
            return "NOT_STARTED";
        }
        if (attempt != null && STATUS_GRADED.equals(attempt.getStatus())) {
            return "GRADED";
        }
        if (now.isAfter(exam.getEndTime())) {
            return "ENDED";
        }
        return "IN_PROGRESS";
    }

    private List<ExamQuestionResponse> loadQuestionsForDisplay(Long examId) {
        List<ExamPaperQuestionEntity> rows = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestionEntity>()
                        .eq(ExamPaperQuestionEntity::getExamId, examId)
                        .orderByAsc(ExamPaperQuestionEntity::getSortOrder));
        List<ExamQuestionResponse> list = new ArrayList<>();
        for (ExamPaperQuestionEntity row : rows) {
            Map<String, Object> snap = readSnapshot(row.getQuestionSnapshot());
            list.add(ExamQuestionResponse.builder()
                    .id(row.getQuestionId())
                    .questionType(String.valueOf(snap.get("questionType")))
                    .stem(String.valueOf(snap.get("stem")))
                    .options(objectMapper.convertValue(snap.get("options"), new TypeReference<>() {
                    }))
                    .score(row.getScore())
                    .build());
        }
        return list;
    }

    private ExamEntity requireExam(Long examId) {
        ExamEntity exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_FOUND, "Exam not found: " + examId);
        }
        return exam;
    }

    private Map<String, Object> readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid snapshot", e);
        }
    }
}
