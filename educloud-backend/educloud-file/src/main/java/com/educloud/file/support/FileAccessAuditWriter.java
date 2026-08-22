package com.educloud.file.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.file.entity.FileAccessAuditEntity;
import com.educloud.file.mapper.FileAccessAuditMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.Objects;

/**
 * 文件访问审计写入器：向 file_access_audit 追加只读事实记录。
 *
 * <p>依据：M04 设计规格第 5/9 节 —— action 取值 GRANT_SINGLE、GRANT_BATCH_DENIED、
 * DELETE、DELETE_FORCE、STORAGE_TEST；result=SUCCESS/FAILURE；request_id 由
 * {@link RequestContextAccessor} 解析（无请求上下文回退 UUID），ip 取
 * X-Forwarded-For 首段或 remoteAddr（无 Servlet 上下文时为 NULL）。
 * 本任务仅消费 GRANT 相关 action，其余 action 由后续任务复用 {@link #write}。</p>
 */
@Component
public class FileAccessAuditWriter {

    public static final String ACTION_GRANT_SINGLE = "GRANT_SINGLE";
    public static final String ACTION_GRANT_BATCH_DENIED = "GRANT_BATCH_DENIED";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_DELETE_FORCE = "DELETE_FORCE";
    public static final String ACTION_STORAGE_TEST = "STORAGE_TEST";

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILURE = "FAILURE";

    private final FileAccessAuditMapper auditMapper;
    private final RequestContextAccessor requestContext;
    private final Clock clock;

    public FileAccessAuditWriter(
            FileAccessAuditMapper auditMapper,
            RequestContextAccessor requestContext,
            Clock clock) {
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
        this.requestContext = Objects.requireNonNull(requestContext, "requestContext");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** GRANT_SINGLE 审计：grant 评估完成（GRANTED=true / UNAVAILABLE=false）。 */
    public void writeGrantSingle(Long fileId, Long userId, boolean success) {
        write(fileId, userId, ACTION_GRANT_SINGLE,
                success ? RESULT_SUCCESS : RESULT_FAILURE);
    }

    /** GRANT_BATCH_DENIED 审计：批量 grant 因伪造/越权整批 403。 */
    public void writeGrantBatchDenied(Long fileId, Long userId) {
        write(fileId, userId, ACTION_GRANT_BATCH_DENIED, RESULT_FAILURE);
    }

    /** 通用审计写入；后续任务（DELETE/DELETE_FORCE/STORAGE_TEST）复用。 */
    public void write(Long fileId, Long userId, String action, String result) {
        FileAccessAuditEntity entity = new FileAccessAuditEntity();
        entity.setId(IdWorker.getId());
        entity.setFileId(fileId);
        entity.setUserId(userId);
        entity.setAction(action);
        entity.setResult(result);
        entity.setIp(clientIp());
        entity.setRequestId(requestContext.requestId());
        entity.setOccurredAt(clock.instant());
        auditMapper.insert(entity);
    }

    private String clientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            if (request != null) {
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        }
        return null;
    }
}
