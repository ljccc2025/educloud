package com.educloud.content.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.content.dto.request.ExamCreateRequest;
import com.educloud.content.dto.request.ExamQuestionRequest;
import com.educloud.content.dto.response.ExamBankQuestionResponse;
import com.educloud.content.dto.response.ExamResponse;
import com.educloud.content.security.JwtSecurityUtils;
import com.educloud.content.service.ExamBankService;
import com.educloud.content.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教师端考试接口（规格 §4.1）：题库 CRUD + 考试 CRUD + 组卷发布。
 * 路径 /api/v1/teacher/exams/** 网关已预留；需 ROLE_TEACHER / ROLE_ADMIN。
 */
@RestController
@RequestMapping("/api/v1/teacher/exams")
@RequiredArgsConstructor
public class ExamTeacherController {

    private final ExamBankService bankService;
    private final ExamService examService;
    private final ApiResponseFactory responses;

    @PostMapping("/exam-bank/questions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<ExamBankQuestionResponse> createQuestion(
            @Valid @RequestBody ExamQuestionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(bankService.createQuestion(request, JwtSecurityUtils.userId(jwt)));
    }

    @GetMapping("/exam-bank/questions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<List<ExamBankQuestionResponse>> listQuestions(
            @RequestParam(required = false) Long courseId) {
        return responses.success(bankService.listQuestions(courseId));
    }

    @PutMapping("/exam-bank/questions/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<ExamBankQuestionResponse> updateQuestion(
            @PathVariable Long id,
            @Valid @RequestBody ExamQuestionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(bankService.updateQuestion(id, request, JwtSecurityUtils.userId(jwt)));
    }

    @DeleteMapping("/exam-bank/questions/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Void> deleteQuestion(@PathVariable Long id,
                                            @AuthenticationPrincipal Jwt jwt) {
        bankService.deleteQuestion(id, JwtSecurityUtils.userId(jwt));
        return responses.success(null);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<ExamResponse> createExam(
            @Valid @RequestBody ExamCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(examService.createExam(request, JwtSecurityUtils.userId(jwt)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<List<ExamResponse>> listExams(@AuthenticationPrincipal Jwt jwt) {
        return responses.success(examService.listTeacherExams(JwtSecurityUtils.userId(jwt)));
    }

    @PutMapping("/{examId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<ExamResponse> updateExam(@PathVariable Long examId,
                                                @Valid @RequestBody ExamCreateRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        return responses.success(examService.updateExam(examId, request, JwtSecurityUtils.userId(jwt)));
    }

    @PostMapping("/{examId}/publish")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Void> publishExam(@PathVariable Long examId,
                                         @AuthenticationPrincipal Jwt jwt) {
        examService.publishExam(examId, JwtSecurityUtils.userId(jwt));
        return responses.success(null);
    }

    @DeleteMapping("/{examId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Void> deleteExam(@PathVariable Long examId,
                                        @AuthenticationPrincipal Jwt jwt) {
        examService.deleteExam(examId, JwtSecurityUtils.userId(jwt));
        return responses.success(null);
    }
}
