package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.response.ExamQuestionResponse;
import com.educloud.content.dto.response.ExamResponse;
import com.educloud.content.entity.ExamAttemptEntity;
import com.educloud.content.entity.ExamEntity;
import com.educloud.content.entity.ExamPaperQuestionEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.ExamAttemptMapper;
import com.educloud.content.mapper.ExamMapper;
import com.educloud.content.mapper.ExamPaperQuestionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExamService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_GRADED = "GRADED";

    private final ExamMapper examMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamAttemptMapper attemptMapper;
    private final ObjectMapper objectMapper;

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
        ExamAttemptEntity attempt = attemptMapper.selectOne(
                new LambdaQueryWrapper<ExamAttemptEntity>()
                        .eq(ExamAttemptEntity::getExamId, examId)
                        .eq(ExamAttemptEntity::getStudentId, studentId));
        return toStudentView(exam, attempt);
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
