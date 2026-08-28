package com.educloud.content.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.content.dto.request.ExamCreateRequest;
import com.educloud.content.dto.request.ExamQuestionRequest;
import com.educloud.content.dto.response.ExamBankQuestionResponse;
import com.educloud.content.dto.response.ExamResponse;
import com.educloud.content.security.TeacherAccessGuard;
import com.educloud.content.service.ExamBankService;
import com.educloud.content.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教师端考试接口（规格 §4.1）：题库 CRUD + 考试 CRUD + 组卷发布。
 * 路径 /api/v1/teacher/exams/** 网关已预留；鉴权经 {@link TeacherAccessGuard}（角色/权限 + 课程归属校验）。
 */
@RestController
@RequestMapping("/api/v1/teacher/exams")
@RequiredArgsConstructor
public class ExamTeacherController {

    private final ExamBankService bankService;
    private final ExamService examService;
    private final ApiResponseFactory responses;
    private final TeacherAccessGuard teacherAccessGuard;

    @PostMapping("/exam-bank/questions")
    public ApiResponse<ExamBankQuestionResponse> createQuestion(
            @Valid @RequestBody ExamQuestionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt, request.getCourseId());
        return responses.success(bankService.createQuestion(request, teacherId));
    }

    @GetMapping("/exam-bank/questions")
    public ApiResponse<List<ExamBankQuestionResponse>> listQuestions(
            @RequestParam(required = false) Long courseId,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        return responses.success(bankService.listQuestions(courseId, teacherId));
    }

    @PutMapping("/exam-bank/questions/{id}")
    public ApiResponse<ExamBankQuestionResponse> updateQuestion(
            @PathVariable Long id,
            @Valid @RequestBody ExamQuestionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        return responses.success(bankService.updateQuestion(id, request, teacherId));
    }

    @DeleteMapping("/exam-bank/questions/{id}")
    public ApiResponse<Void> deleteQuestion(@PathVariable Long id,
                                            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        bankService.deleteQuestion(id, teacherId);
        return responses.success(null);
    }

    @PostMapping
    public ApiResponse<ExamResponse> createExam(
            @Valid @RequestBody ExamCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt, request.getCourseId());
        return responses.success(examService.createExam(request, teacherId));
    }

    @GetMapping
    public ApiResponse<List<ExamResponse>> listExams(@AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        return responses.success(examService.listTeacherExams(teacherId));
    }

    @PutMapping("/{examId}")
    public ApiResponse<ExamResponse> updateExam(@PathVariable Long examId,
                                                @Valid @RequestBody ExamCreateRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        return responses.success(examService.updateExam(examId, request, teacherId));
    }

    @PostMapping("/{examId}/publish")
    public ApiResponse<Void> publishExam(@PathVariable Long examId,
                                         @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        examService.publishExam(examId, teacherId);
        return responses.success(null);
    }

    @DeleteMapping("/{examId}")
    public ApiResponse<Void> deleteExam(@PathVariable Long examId,
                                        @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        examService.deleteExam(examId, teacherId);
        return responses.success(null);
    }
}
