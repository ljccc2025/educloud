package com.educloud.recommendation.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RecommendationResponse {
    private Integer configVersion;
    private List<RecommendationItem> items;
}
