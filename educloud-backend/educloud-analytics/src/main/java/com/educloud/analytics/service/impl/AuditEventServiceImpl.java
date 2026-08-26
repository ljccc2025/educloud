package com.educloud.analytics.service.impl;

import com.educloud.analytics.dto.response.admin.AuditLogPageResponse;
import com.educloud.analytics.entity.AuditEventReadModelEntity;
import com.educloud.analytics.mapper.AuditEventReadModelMapper;
import com.educloud.analytics.service.AuditEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditEventServiceImpl implements AuditEventService {

    private final AuditEventReadModelMapper auditEventReadModelMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordAuditEvent(AuditEventReadModelEntity event) {
        if (event == null) return;
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(LocalDateTime.now());
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(LocalDateTime.now());
        }
        auditEventReadModelMapper.insert(event);
        log.info("Recorded AuditEvent: auditId={}, action={}, actor={}", event.getAuditId(), event.getAction(), event.getActorId());
    }

    @Override
    public AuditLogPageResponse searchAuditLogs(
            int page,
            int pageSize,
            String level,
            String keyword,
            String sourceService,
            String actorId,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        int safePage = Math.max(1, page);
        int safePageSize = (pageSize > 0 && pageSize <= 100) ? pageSize : 15;
        int offset = (safePage - 1) * safePageSize;

        long total = auditEventReadModelMapper.countAuditLogs(keyword, level, sourceService, actorId, startDate, endDate);
        if (total == 0) {
            return AuditLogPageResponse.builder()
                    .total(0L)
                    .page(safePage)
                    .pageSize(safePageSize)
                    .list(Collections.emptyList())
                    .build();
        }

        List<AuditEventReadModelEntity> entities = auditEventReadModelMapper.searchAuditLogs(
                keyword, level, sourceService, actorId, startDate, endDate, offset, safePageSize
        );

        List<AuditLogPageResponse.AuditLogItem> items = entities.stream().map(e -> AuditLogPageResponse.AuditLogItem.builder()
                .id(String.valueOf(e.getId()))
                .timestamp(e.getOccurredAt() != null ? e.getOccurredAt().toString().replace("T", " ") : "")
                .level(e.getLevel() != null ? e.getLevel() : "INFO")
                .operator(e.getActorId() != null ? e.getActorId() : "system")
                .sourceService(e.getSourceService() != null ? e.getSourceService() : "educloud")
                .action(e.getAction() != null ? e.getAction() : "ACTION")
                .target(e.getResourceId() != null ? e.getResourceId() : (e.getResourceType() != null ? e.getResourceType() : "-"))
                .ip(e.getClientIp() != null ? e.getClientIp() : "127.0.0.1")
                .detail(e.getPayloadJson() != null ? e.getPayloadJson() : "-")
                .build()
        ).toList();

        return AuditLogPageResponse.builder()
                .total(total)
                .page(safePage)
                .pageSize(safePageSize)
                .list(items)
                .build();
    }
}
