package com.educloud.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentAuditRejectRequest {
    @NotBlank(message = "Reject reason must not be blank")
    @Size(max = 512, message = "Reject reason must not exceed 512 characters")
    private String rejectReason;
}
