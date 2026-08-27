package com.educloud.content.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.content.dto.response.CertificateResponse;
import com.educloud.content.entity.CourseCertificateEntity;
import com.educloud.content.security.JwtSecurityUtils;
import com.educloud.content.service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 完课证书查询接口（角色化动态流阶段 3，规格 §6.3）：
 * <ul>
 *   <li>{@code GET /api/v1/content/certificates} —— 当前学员的证书列表</li>
 *   <li>{@code GET /api/v1/content/certificates/{certNo}} —— 证书详情</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;
    private final ApiResponseFactory responses;

    @GetMapping("/certificates")
    public ApiResponse<List<CertificateResponse>> listMyCertificates(@AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        List<CertificateResponse> list = certificateService.listCertificates(studentId).stream()
                .map(CertificateController::toResponse)
                .toList();
        return responses.success(list);
    }

    @GetMapping("/certificates/{certNo}")
    public ApiResponse<CertificateResponse> getCertificate(@PathVariable String certNo) {
        return responses.success(toResponse(certificateService.getByCertNo(certNo)));
    }

    private static CertificateResponse toResponse(CourseCertificateEntity certificate) {
        CertificateResponse response = new CertificateResponse();
        response.setCertNo(certificate.getCertNo());
        response.setCourseId(certificate.getCourseId());
        response.setCourseTitle(certificate.getCourseTitle());
        response.setIssuedAt(certificate.getIssuedAt());
        return response;
    }
}
