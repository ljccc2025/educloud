package com.educloud.search.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 搜索建议响应模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 建议项列表 */
    @JsonProperty("suggestions")
    @Builder.Default
    private List<SuggestItem> suggestions = new ArrayList<>();

    public static SuggestResponse empty() {
        return SuggestResponse.builder()
                .suggestions(new ArrayList<>())
                .build();
    }

    public static SuggestResponse of(List<SuggestItem> suggestions) {
        return SuggestResponse.builder()
                .suggestions(suggestions != null ? suggestions : new ArrayList<>())
                .build();
    }
}
