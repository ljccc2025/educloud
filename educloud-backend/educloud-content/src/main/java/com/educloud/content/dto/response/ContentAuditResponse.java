package com.educloud.content.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentAuditResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long courseId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentRevisionId;

    private Integer revisionNo;

    private String snapshotJson;

    private String status;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long submittedBy;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long reviewedBy;

    private String rejectReason;

    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    private LocalDateTime withdrawnAt;
}
