package com.educloud.live.dto.response;

import com.educloud.live.enums.LiveProviderType;
import com.educloud.live.enums.LiveRoomStatus;
import com.educloud.live.spi.model.LiveStreamPlayUrls;
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
public class LiveRoomDetailResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long courseId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long teacherId;

    private String title;
    private String description;
    private LocalDateTime scheduledStartAt;
    private LocalDateTime scheduledEndAt;
    private LiveRoomStatus status;
    private LiveProviderType providerType;
    private String streamKey;
    private Boolean allowChat;
    private Integer currentOnlineViewers;
    private Integer peakViewers;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentSessionId;

    private LiveStreamPushUrl pushInfo;
    private LiveStreamPlayUrls playInfo;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
