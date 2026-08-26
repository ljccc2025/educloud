package com.educloud.search.service;

import com.educloud.search.dto.response.SuggestResponse;

/**
 * 搜索框实时智能建议与前缀自动补全服务接口
 */
public interface SuggestService {

    /**
     * 根据输入前缀获取匹配建议列表
     *
     * @param prefix 输入前缀关键词
     * @param limit  最大建议条数（默认 8，最大 20）
     * @return 建议项列表响应
     */
    SuggestResponse suggest(String prefix, Integer limit);
}
