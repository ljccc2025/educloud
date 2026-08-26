package com.educloud.analytics.controller;

import com.educloud.analytics.dto.response.admin.AuditLogPageResponse;
import com.educloud.analytics.service.AuditEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditEventController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TestInfrastructure.class)
class AuditEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditEventService auditEventService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("测试全平台集中式审计日志多维检索 GET /api/v1/analytics/admin/audit/logs")
    void testSearchAuditLogs() throws Exception {
        AuditLogPageResponse response = AuditLogPageResponse.builder()
                .total(1L)
                .page(1)
                .pageSize(15)
                .list(List.of(
                        AuditLogPageResponse.AuditLogItem.builder()
                                .id("101")
                                .level("INFO")
                                .operator("teacher_01")
                                .action("COURSE_PUBLISH")
                                .target("course_501")
                                .sourceService("educloud-course")
                                .build()
                ))
                .build();

        when(auditEventService.searchAuditLogs(anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/analytics/admin/audit/logs")
                        .param("keyword", "COURSE")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].action").value("COURSE_PUBLISH"));
    }
}
