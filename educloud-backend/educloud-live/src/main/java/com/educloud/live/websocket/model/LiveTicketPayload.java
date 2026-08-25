package com.educloud.live.websocket.model;

import com.educloud.live.enums.LiveSenderRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveTicketPayload {
    private Long roomId;
    private Long userId;
    private LiveSenderRole role;
    private String nickname;
    private LocalDateTime issuedAt;
}
