package com.educloud.file.support;

import com.educloud.common.web.RequestContextAccessor;
import com.educloud.file.entity.FileAccessAuditEntity;
import com.educloud.file.mapper.FileAccessAuditMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M04 任务 6：FileAccessAuditWriter 单元测试 —— 审计实体映射（action/result/ip/requestId/occurredAt）。
 *
 * <p>依据：2026-08-22-educloud-file-design.md 第 5 节 —— file_access_audit 为只追加事实记录；
 * request_id 由 RequestContextAccessor 解析（无请求上下文时回退 UUID），ip 无 Servlet 上下文时为 NULL。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileAccessAuditWriterTest {

    private static final Instant NOW = Instant.parse("2026-08-22T11:30:00Z");

    @Mock
    private FileAccessAuditMapper auditMapper;
    @Mock
    private RequestContextAccessor requestContext;

    private FileAccessAuditWriter writer;

    @BeforeEach
    void setUp() {
        when(requestContext.requestId()).thenReturn("req-0001");
        writer = new FileAccessAuditWriter(
                auditMapper, requestContext, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void writeGrantSinglePersistsSuccessAuditWithRequestContext() {
        writer.writeGrantSingle(1001L, 7L, true);

        FileAccessAuditEntity entity = capturedEntity();
        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getFileId()).isEqualTo(1001L);
        assertThat(entity.getUserId()).isEqualTo(7L);
        assertThat(entity.getAction()).isEqualTo("GRANT_SINGLE");
        assertThat(entity.getResult()).isEqualTo("SUCCESS");
        assertThat(entity.getRequestId()).isEqualTo("req-0001");
        assertThat(entity.getOccurredAt()).isEqualTo(NOW);
        // 单测无 Servlet 请求上下文 → ip 为 NULL（设计允许）
        assertThat(entity.getIp()).isNull();
    }

    @Test
    void writeGrantSinglePersistsFailureAudit() {
        writer.writeGrantSingle(1001L, 7L, false);

        FileAccessAuditEntity entity = capturedEntity();
        assertThat(entity.getAction()).isEqualTo("GRANT_SINGLE");
        assertThat(entity.getResult()).isEqualTo("FAILURE");
    }

    @Test
    void writeGrantBatchDeniedPersistsFailureAuditForAnonymousSubject() {
        writer.writeGrantBatchDenied(2002L, null);

        FileAccessAuditEntity entity = capturedEntity();
        assertThat(entity.getFileId()).isEqualTo(2002L);
        assertThat(entity.getUserId()).isNull();
        assertThat(entity.getAction()).isEqualTo("GRANT_BATCH_DENIED");
        assertThat(entity.getResult()).isEqualTo("FAILURE");
        assertThat(entity.getRequestId()).isEqualTo("req-0001");
    }

    @Test
    void genericWriteSupportsFutureDeleteAndStorageTestActions() {
        writer.write(3003L, 9L, FileAccessAuditWriter.ACTION_DELETE, FileAccessAuditWriter.RESULT_SUCCESS);

        FileAccessAuditEntity entity = capturedEntity();
        assertThat(entity.getAction()).isEqualTo("DELETE");
        assertThat(entity.getResult()).isEqualTo("SUCCESS");
        assertThat(entity.getUserId()).isEqualTo(9L);
    }

    private FileAccessAuditEntity capturedEntity() {
        ArgumentCaptor<FileAccessAuditEntity> captor =
                ArgumentCaptor.forClass(FileAccessAuditEntity.class);
        verify(auditMapper).insert(captor.capture());
        return captor.getValue();
    }
}
