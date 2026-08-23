package com.educloud.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 审核驳回请求体（POST /api/v1/course-audits/{id}/reject，M05 任务 9）。
 *
 * <p>依据：规格 §6 —— 驳回原因必填（REVIEW_REJECT_REASON_REQUIRED 400）；@NotBlank
 * 兜住空串/纯空白，@Size 对齐 course_audit_submission.reason VARCHAR(512)。服务层
 * 二次校验原因非空并抛域错误码（与 @Valid 的 VALIDATION_FAILED 语义互补）。</p>
 */
public record AuditRejectRequest(
        @NotBlank(message = "reason is required")
        @Size(max = 512, message = "reason must be at most 512 characters")
        String reason) {
}
