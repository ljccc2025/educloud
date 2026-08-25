package com.educloud.live.feign.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadGrantRequest {
    private String subjectType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long subjectUserId;
    private String ownerType;
    private String ownerId;
    private String purpose;
    private Long requestedTtlSeconds;
}
