package com.educloud.live.websocket.model;

import com.educloud.live.enums.LiveSenderRole;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage<T> {

    private String type;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderId;

    private String senderName;
    private LiveSenderRole senderRole;
    private T payload;
    private long timestamp;
}
