package com.educloud.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundAuditRequest {

    @NotNull(message = "审核结果不能为空")
    private Boolean approve;

    private String remark;
}
