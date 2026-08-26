package com.educloud.analytics.service;

import com.educloud.analytics.dto.response.admin.AuditLogPageResponse;
import com.educloud.analytics.entity.AuditEventReadModelEntity;

import java.time.LocalDateTime;

public interface AuditEventService {

    void recordAuditEvent(AuditEventReadModelEntity event);

    AuditLogPageResponse searchAuditLogs(
            int page,
            int pageSize,
            String level,
            String keyword,
            String sourceService,
            String actorId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
}
