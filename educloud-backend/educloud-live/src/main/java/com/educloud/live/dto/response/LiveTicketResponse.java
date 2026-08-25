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
public class LiveTicketResponse {

    private String ticket;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;

    private String wsEndpoint;
    private long expiresInSeconds;
    private LocalDateTime expiresAt;
}
