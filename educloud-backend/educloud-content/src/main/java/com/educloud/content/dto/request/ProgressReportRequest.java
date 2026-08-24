package com.educloud.content.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProgressReportRequest {
    @NotNull(message = "Position seconds must not be null")
    @Min(value = 0, message = "Position seconds must be non-negative")
    private Integer positionSeconds;

    @NotNull(message = "Watched delta seconds must not be null")
    @Min(value = 0, message = "Watched delta seconds must be non-negative")
    private Integer watchedDeltaSeconds;

    private Boolean completed;

    private LocalDateTime eventAt;
}
