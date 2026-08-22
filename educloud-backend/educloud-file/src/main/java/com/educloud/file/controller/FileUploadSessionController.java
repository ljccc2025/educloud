package com.educloud.file.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.web.RequestContext;
import com.educloud.file.dto.request.CreateUploadSessionRequest;
import com.educloud.file.dto.response.FileObjectResponse;
import com.educloud.file.dto.response.UploadSessionResponse;
import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.service.FileObjectService;
import com.educloud.file.service.UploadSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 对外上传会话控制器：创建会话（presigned PUT）与确认完成。
 *
 * <p>依据：M04 设计规格 6.1 节 —— 两个端点都要求 file:upload；uploaderId 恒取
 * 已验证 JWT 的 sub（服务端不接受客户端身份头）；create 从 servlet request 取
 * requestId/ip 供观测日志使用（会话实体无对应列，事件载荷留任务 11）。
 * complete 委托 {@link FileObjectService#completeUpload}，异常由任务 7 统一信封化。</p>
 */
@RestController
@RequestMapping("/api/v1/file-upload-sessions")
public class FileUploadSessionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUploadSessionController.class);

    private final UploadSessionService uploadSessionService;
    private final FileObjectService fileObjectService;
    private final ApiResponseFactory responses;

    public FileUploadSessionController(
            UploadSessionService uploadSessionService,
            FileObjectService fileObjectService,
            ApiResponseFactory responses) {
        this.uploadSessionService = Objects.requireNonNull(uploadSessionService, "uploadSessionService");
        this.fileObjectService = Objects.requireNonNull(fileObjectService, "fileObjectService");
        this.responses = Objects.requireNonNull(responses, "responses");
    }

    @PostMapping
    @PreAuthorize("hasAuthority('file:upload')")
    public ApiResponse<UploadSessionResponse> create(
            @Valid @RequestBody CreateUploadSessionRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        Long uploaderId = subjectUserId(jwt);
        String requestId = requestId(servletRequest);
        String ip = servletRequest.getRemoteAddr();
        LOGGER.debug("create upload session uploaderId={} requestId={} clientIp={}",
                uploaderId, requestId, ip);
        return responses.success(uploadSessionService.create(uploaderId, request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('file:upload')")
    public ApiResponse<FileObjectResponse> complete(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long uploaderId = subjectUserId(jwt);
        FileObjectEntity object = fileObjectService.completeUpload(uploaderId, id);
        return responses.success(FileObjectResponse.from(object));
    }

    private static Long subjectUserId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHENTICATED,
                    "JWT subject 必须为数字: " + jwt.getSubject());
        }
    }

    private static String requestId(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        if (attribute instanceof String text && !text.isBlank()) {
            return text;
        }
        return request.getHeader(RequestContext.REQUEST_ID_HEADER);
    }
}
