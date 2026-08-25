package com.educloud.live.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveRoomCreateRequest {

    @NotNull(message = "课程ID不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long courseId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long teacherId;

    @NotBlank(message = "直播间标题不能为空")
    private String title;

    private String description;

    @NotNull(message = "计划开播时间不能为空")
    private LocalDateTime scheduledStartAt;

    @NotNull(message = "计划结课时间不能为空")
    private LocalDateTime scheduledEndAt;
}
