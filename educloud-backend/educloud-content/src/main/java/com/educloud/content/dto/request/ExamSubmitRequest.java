package com.educloud.content.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ExamSubmitRequest {
    /** questionId -> 选项索引数组 */
    private Map<Long, List<Integer>> answers;
    @Min(0)
    private Integer tabSwitchCount;
}
