package com.educloud.live.dto.response;

import com.educloud.live.enums.LiveReplayStatus;
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
public class LiveReplayResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    private String title;
    private Long durationSeconds;
    private Long sizeBytes;
    private LiveReplayStatus status;
    private String playUrl;
    private LocalDateTime availableAt;
    private LocalDateTime createdAt;
}
