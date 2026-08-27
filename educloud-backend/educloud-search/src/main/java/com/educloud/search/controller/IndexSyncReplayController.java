package com.educloud.search.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.search.service.DlqRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 索引同步死信手动重放内部端点（/internal/**，由 InternalApiFilter 鉴权）
 * 供运维在 DLQ 死信堆积时按 id 手动重放失败记录。
 */
@RestController
@RequestMapping("/internal/index-sync")
@RequiredArgsConstructor
public class IndexSyncReplayController {

    private final DlqRecoveryService dlqRecoveryService;
    private final ApiResponseFactory responses;

    /**
     * 按记录 ID 手动重放单条死信失败记录
     *
     * @param id index_sync_failure 记录主键
     * @return 重放结果（含最新状态 RESOLVED / PENDING / DEAD / NOT_FOUND）
     */
    @PostMapping("/replay/{id}")
    public ApiResponse<DlqRecoveryService.ReplayResult> replayById(@PathVariable("id") Long id) {
        return responses.success(dlqRecoveryService.replayById(id));
    }

    /**
     * 查询最近的死信失败记录（运维排查）
     *
     * @param limit 返回条数上限（默认 20，最大 100）
     * @return 失败记录列表
     */
    @GetMapping("/failures")
    public ApiResponse<List<DlqRecoveryService.ReplayResult>> listRecentFailures(
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : 20;
        return responses.success(dlqRecoveryService.listRecentFailures(effectiveLimit));
    }
}
