package com.educloud.payment.dto.request;

import com.educloud.payment.enums.ResolveAction;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconcileDiffResolveRequest {

    @NotNull(message = "平账处理动作不能为空")
    private ResolveAction action;

    private String remark;
}
