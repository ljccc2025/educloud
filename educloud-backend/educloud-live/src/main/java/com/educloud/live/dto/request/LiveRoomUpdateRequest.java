package com.educloud.live.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveRoomUpdateRequest {

    private String title;
    private String description;
    private LocalDateTime scheduledStartAt;
    private LocalDateTime scheduledEndAt;
    private Boolean allowChat;
}
