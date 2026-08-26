package com.educloud.search.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Elasticsearch 嵌套文档：课件/章节模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 课件雪花 ID (String 避免前端精度丢失) */
    private String id;

    /** 课件标题 */
    private String title;

    /** 所属章节标题 */
    private String chapterTitle;

    /** 是否支持免费试看 */
    private Boolean isPreview;
}
