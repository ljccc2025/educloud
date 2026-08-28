package com.educloud.content.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.content.dto.request.ExamSubmitRequest;
import com.educloud.content.dto.response.ExamAttemptResponse;
import com.educloud.content.dto.response.ExamResponse;
import com.educloud.content.entity.ExamAttemptEntity;
import com.educloud.content.security.JwtSecurityUtils;
import com.educloud.content.service.ExamAttemptService;
import com.educloud.content.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生端考试接口（规格 §4.2）：
 * 列表 / 详情（不含答案） / 开始考试 / 交卷判分 / 成绩答卷。
 */
@RestController
@RequestMapping("/api/v1/me/exams")
@RequiredArgsConstructor
public class ExamStudentController {

    private final ExamService examService;
    private final ExamAttemptService attemptService;
    private final ApiResponseFactory responses;

    @GetMapping
    public ApiResponse<List<ExamResponse>> listMyExams(@AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        return responses.success(examService.listStudentExams(studentId));
    }

    @GetMapping("/{examId}")
    public ApiResponse<ExamResponse> getExam(@PathVariable Long examId,
                                             @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        return responses.success(examService.getStudentExam(examId, studentId));
    }

    @PostMapping("/{examId}/attempts")
    public ApiResponse<ExamAttemptResponse> startAttempt(@PathVariable Long examId,
                                                         @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        ExamAttemptEntity attempt = attemptService.startAttempt(examId, studentId);
        return responses.success(ExamAttemptResponse.builder()
                .id(attempt.getId())
                .examId(attempt.getExamId())
                .status(attempt.getStatus())
                .startedAt(attempt.getStartedAt())
                .build());
    }

    @PostMapping("/{examId}/attempts/{attemptId}/submit")
    public ApiResponse<ExamAttemptResponse> submitAttempt(@PathVariable Long examId,
                                                          @PathVariable Long attemptId,
                                                          @RequestBody ExamSubmitRequest request,
                                                          @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        return responses.success(attemptService.submitAttempt(examId, attemptId, studentId, request));
    }

    @GetMapping("/{examId}/attempts/{attemptId}")
    public ApiResponse<ExamAttemptResponse> getAttempt(@PathVariable Long examId,
                                                       @PathVariable Long attemptId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        return responses.success(attemptService.getAttemptResult(examId, attemptId, studentId));
    }
}
