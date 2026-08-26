package com.educloud.search.service;

import com.educloud.search.dto.response.IndexTaskProgressResponse;

import java.util.List;

/**
 * 管理端全量索引平滑重建服务接口
 * 负责调度全量数据抽取、ES 批量索引构建以及零停机别名原子切换。
 */
public interface IndexRebuildService {

    /**
     * 触发全量索引平滑重建任务（异步后台执行）
     *
     * @param operator 触发操作人
     * @return 初始任务进度对象
     */
    IndexTaskProgressResponse triggerFullRebuild(String operator);

    /**
     * 查询指定任务的重建进度详情
     *
     * @param taskNo 任务唯一编号
     * @return 任务进度详情，若任务不存在则返回 null
     */
    IndexTaskProgressResponse getTaskProgress(String taskNo);

    /**
     * 查询最近触发的索引任务列表
     *
     * @param limit 返回记录数限制（最大 100）
     * @return 任务列表（按创建时间倒序排列）
     */
    List<IndexTaskProgressResponse> listRecentTasks(int limit);

    /**
     * 执行全量重建的核心流程（供异步线程或单元测试显式调用）
     *
     * @param taskNo       任务唯一编号
     * @param newIndexName 目标物理索引名称
     */
    void executeFullRebuild(String taskNo, String newIndexName);
}
