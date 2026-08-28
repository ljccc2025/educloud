package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamBankService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final ExamBankQuestionMapper questionMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamMapper examMapper;
    private final ObjectMapper objectMapper;

    public ExamBankQuestionResponse createQuestion(ExamQuestionRequest request, Long teacherId) {
        ExamBankQuestionEntity entity = new ExamBankQuestionEntity();
        entity.setCourseId(request.getCourseId());
        entity.setTeacherId(teacherId);
        entity.setQuestionType(request.getQuestionType());
        entity.setStem(request.getStem());
        entity.setOptions(writeJson(request.getOptions()));
        entity.setAnswer(writeJson(request.getAnswer()));
        entity.setAnalysis(request.getAnalysis());
        entity.setDefaultScore(request.getDefaultScore() == null ? 5 : request.getDefaultScore());
        entity.setStatus(STATUS_ENABLED);
        questionMapper.insert(entity);
        return toResponse(entity);
    }

    public List<ExamBankQuestionResponse> listQuestions(Long courseId) {
        return questionMapper.selectList(
                        new LambdaQueryWrapper<ExamBankQuestionEntity>()
                                .eq(courseId != null, ExamBankQuestionEntity::getCourseId, courseId)
                                .eq(ExamBankQuestionEntity::getStatus, STATUS_ENABLED)
                                .orderByDesc(ExamBankQuestionEntity::getId))
                .stream().map(this::toResponse).toList();
    }

    public ExamBankQuestionResponse updateQuestion(Long id, ExamQuestionRequest request, Long teacherId) {
        ExamBankQuestionEntity entity = requireQuestion(id);
        if (!entity.getTeacherId().equals(teacherId)) {
            throw new BusinessException(ContentErrorCode.TEACHER_ACCESS_DENIED,
                    "You are not authorized to update this question: " + id);
        }
        entity.setQuestionType(request.getQuestionType());
        entity.setStem(request.getStem());
        entity.setOptions(writeJson(request.getOptions()));
        entity.setAnswer(writeJson(request.getAnswer()));
        entity.setAnalysis(request.getAnalysis());
        if (request.getDefaultScore() != null) {
            entity.setDefaultScore(request.getDefaultScore());
        }
        questionMapper.updateById(entity);
        return toResponse(entity);
    }

    /** 软删：被已发布考试引用的题目拒绝删除。 */
    public void deleteQuestion(Long id, Long teacherId) {
        ExamBankQuestionEntity entity = requireQuestion(id);
        if (!entity.getTeacherId().equals(teacherId)) {
            throw new BusinessException(ContentErrorCode.TEACHER_ACCESS_DENIED,
                    "You are not authorized to delete this question: " + id);
        }
        Long referencingExamId = findReferencingPublishedExam(id);
        if (referencingExamId != null) {
            throw new BusinessException(ContentErrorCode.EXAM_QUESTION_IN_USE,
                    "Question " + id + " referenced by published exam " + referencingExamId);
        }
        entity.setStatus("DISABLED");
        questionMapper.updateById(entity);
    }

    private Long findReferencingPublishedExam(Long questionId) {
        List<ExamPaperQuestionEntity> refs = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestionEntity>()
                        .eq(ExamPaperQuestionEntity::getQuestionId, questionId));
        for (ExamPaperQuestionEntity ref : refs) {
            ExamEntity exam = examMapper.selectById(ref.getExamId());
            if (exam != null && STATUS_PUBLISHED.equals(exam.getStatus())) {
                return exam.getId();
            }
        }
        return null;
    }

    private ExamBankQuestionEntity requireQuestion(Long id) {
        ExamBankQuestionEntity entity = questionMapper.selectById(id);
        if (entity == null || !STATUS_ENABLED.equals(entity.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_QUESTION_NOT_FOUND,
                    "Exam bank question not found: " + id);
        }
        return entity;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize field", e);
        }
    }

    private ExamBankQuestionResponse toResponse(ExamBankQuestionEntity entity) {
        return ExamBankQuestionResponse.builder()
                .id(entity.getId())
                .courseId(entity.getCourseId())
                .questionType(entity.getQuestionType())
                .stem(entity.getStem())
                .options(readList(entity.getOptions()))
                .defaultScore(entity.getDefaultScore())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
