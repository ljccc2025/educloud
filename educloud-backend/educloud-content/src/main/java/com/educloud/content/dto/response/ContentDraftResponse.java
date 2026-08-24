package com.educloud.content.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

@Data
public class ContentDraftResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentRootId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long revisionId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long courseId;

    private Integer revisionNo;

    private String revisionStatus;

    private List<ChapterResponse> chapters;
}
