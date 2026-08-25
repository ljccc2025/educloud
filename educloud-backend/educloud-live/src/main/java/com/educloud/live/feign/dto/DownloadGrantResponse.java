package com.educloud.live.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadGrantResponse {
    private String downloadUrl;
    private String token;
    private Long expiresInSeconds;
    private LocalDateTime expiresAt;
}
