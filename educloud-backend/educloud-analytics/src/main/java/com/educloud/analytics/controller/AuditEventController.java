package com.educloud.analytics.controller;

import com.educloud.analytics.dto.response.admin.AuditLogPageResponse;
import com.educloud.analytics.service.AuditEventService;
import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "管理端审计日志接口")
@RestController
@RequestMapping("/api/v1/analytics/admin/audit")
@RequiredArgsConstructor
public class AuditEventController {

    private final AuditEventService auditEventService;
    private final ApiResponseFactory responses;

    @Operation(summary = "全平台集中式操作审计日志检索")
    @GetMapping("/logs")
    public ApiResponse<AuditLogPageResponse> searchAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int pageSize,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sourceService,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        return responses.success(auditEventService.searchAuditLogs(
                page, pageSize, level, keyword, sourceService, actorId, startDate, endDate
        ));
    }
}
