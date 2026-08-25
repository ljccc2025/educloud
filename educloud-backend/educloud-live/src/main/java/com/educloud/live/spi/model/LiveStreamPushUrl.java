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
public class LiveStreamPushUrl {
    private String pushUrl;
    private String streamKey;
    private String token;
    private LocalDateTime expiresAt;
}
