package com.educloud.content.service;

import com.educloud.content.entity.ExamAttemptEntity;
import com.educloud.content.mapper.ExamAttemptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** 超时收敛：扫描超时未交卷的 IN_PROGRESS attempt，按已答内容自动判分（规格 §5.2）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamTimeoutSweeper {

    private final ExamAttemptMapper attemptMapper;
    private final ExamAttemptService attemptService;

    @Scheduled(fixedDelay = 30_000)
    public void sweepExpiredAttempts() {
        List<ExamAttemptEntity> candidates = attemptMapper.selectExpiredInProgress();
        for (ExamAttemptEntity attempt : candidates) {
            try {
                // 超时判定在应用侧（LocalDateTime.now() 与 started_at 同为 JVM 本地时间），
                // 避免 DB 容器 UTC 时钟与 started_at(+08) 直接比较导致刚创建的 attempt 被误判超时。
                if (!attemptService.isExpired(attempt)) {
                    continue;
                }
                // 使用窗口无关的判分路径：考试窗口结束后（end_time 已过 / 状态 CLOSED）仍可收敛判分。
                if (attemptService.timeoutSubmit(attempt) != null) {
                    log.info("Timeout-swept exam attempt: attemptId={}, examId={}, studentId={}",
                            attempt.getId(), attempt.getExamId(), attempt.getStudentId());
                } else {
                    log.debug("Timeout-sweep skipped (concurrently graded): attemptId={}",
                            attempt.getId());
                }
            } catch (Exception e) {
                log.warn("Failed to timeout-sweep attempt {}: {}", attempt.getId(), e.getMessage());
            }
        }
    }
}
