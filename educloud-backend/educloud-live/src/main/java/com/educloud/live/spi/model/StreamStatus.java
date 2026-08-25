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
public class StreamStatus {
    private boolean active;
    private int bitrateKbps;
    private int fps;
    private int onlineViewers;
    private LocalDateTime checkedAt;
}
