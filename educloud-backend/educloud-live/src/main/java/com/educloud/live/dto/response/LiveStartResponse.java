package com.educloud.live.dto.response;

import com.educloud.live.spi.model.LiveStreamPushUrl;
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
public class LiveStartResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    private String streamKey;
    private LiveStreamPushUrl pushInfo;
    private LocalDateTime startedAt;
}
