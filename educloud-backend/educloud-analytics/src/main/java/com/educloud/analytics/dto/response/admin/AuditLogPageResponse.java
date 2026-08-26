package com.educloud.analytics.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogPageResponse {

    private Long total;
    private Integer page;
    private Integer pageSize;
    private List<AuditLogItem> list;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditLogItem {
        private String id;
        private String timestamp;
        private String level;
        private String operator;
        private String sourceService;
        private String action;
        private String target;
        private String ip;
        private String detail;
    }
}
