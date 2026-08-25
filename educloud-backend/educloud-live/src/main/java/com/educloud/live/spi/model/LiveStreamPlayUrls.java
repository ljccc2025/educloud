package com.educloud.live.spi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveStreamPlayUrls {
    private String flvUrl;
    private String hlsUrl;
    private String webrtcUrl;
    private LocalDateTime expiresAt;
}
