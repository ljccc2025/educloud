package com.educloud.content.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.content.dto.request.AssignmentCreateRequest;
import com.educloud.content.dto.request.AssignmentSubmitRequest;
import com.educloud.content.dto.response.AssignmentResponse;
import com.educloud.content.security.JwtSecurityUtils;
import com.educloud.content.service.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final ApiResponseFactory responses;

    @GetMapping("/assignments")
    public ApiResponse<List<AssignmentResponse>> getAllAssignments() {
        return responses.success(assignmentService.getAllAssignments());
    }

    @GetMapping("/assignments/{id}")
    public ApiResponse<AssignmentResponse> getAssignmentById(@PathVariable String id) {
        AssignmentResponse res = assignmentService.getAssignmentById(id);
        return responses.success(res);
    }

    @PostMapping("/assignments")
    public ApiResponse<AssignmentResponse> createAssignment(
            @Valid @RequestBody AssignmentCreateRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = jwt != null ? JwtSecurityUtils.userId(jwt) : 1001L;
        AssignmentResponse res = assignmentService.createAssignment(req, teacherId);
        return responses.success(res);
    }

    @PostMapping("/assignments/{id}/publish")
    public ApiResponse<AssignmentResponse> publishAssignment(@PathVariable String id) {
        AssignmentResponse res = assignmentService.publishAssignment(id);
        return responses.success(res);
    }

    @GetMapping("/me/assignments")
    public ApiResponse<List<AssignmentResponse>> getMyAssignments(@AuthenticationPrincipal Jwt jwt) {
        Long studentId = jwt != null ? JwtSecurityUtils.userId(jwt) : 10L;
        List<AssignmentResponse> list = assignmentService.getStudentAssignments(studentId);
        return responses.success(list);
    }

    @PostMapping("/assignments/{id}/submit")
    public ApiResponse<AssignmentResponse> submitAssignment(
            @PathVariable String id,
            @Valid @RequestBody AssignmentSubmitRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = jwt != null ? JwtSecurityUtils.userId(jwt) : 10L;
        AssignmentResponse res = assignmentService.submitAssignment(id, req, studentId, req.getStudentName());
        return responses.success(res);
    }

    @PostMapping("/assignments/{id}/submissions")
    public ApiResponse<AssignmentResponse> submitAssignmentAlt(
            @PathVariable String id,
            @Valid @RequestBody AssignmentSubmitRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return submitAssignment(id, req, jwt);
    }

    @PutMapping("/submissions/{id}/grade")
    public ApiResponse<Void> gradeSubmission(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        String assignmentId = (String) body.get("assignmentId");
        Number studentIdNum = (Number) body.get("studentId");
        Number scoreNum = (Number) body.get("score");
        String feedback = (String) body.get("feedback");

        Long studentId = studentIdNum != null ? studentIdNum.longValue() : null;
        Integer score = scoreNum != null ? scoreNum.intValue() : 100;

        assignmentService.gradeSubmission(id, studentId, assignmentId, score, feedback);
        return responses.success(null);
    }
}
