package com.educloud.live.dto.response;

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
public class LiveEndResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    private Long durationSeconds;
    private Integer peakViewers;
    private Integer totalViewers;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long replayId;

    private LocalDateTime endedAt;
}
