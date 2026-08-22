package com.educloud.user.messaging;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.educloud.user.entity.InboxEventEntity;
import com.educloud.user.entity.UserProfileEntity;
import com.educloud.user.mapper.InboxEventMapper;
import com.educloud.user.mapper.UserProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FileDeleted Inbox 消费者（M04 任务 14）。
 *
 * <p>小批轮询 PENDING 的 FileDeleted 事件：把匹配 {@code user_profile.avatar_file_id} 的
 * 引用置 NULL，然后标记 PROCESSED（business_effect=AVATAR_CLEARED）；没有匹配行则标记
 * NO_OP。处理失败按短退避（2s×attempt，封顶 300s）在内存中记录下一次尝试时间，
 * 达阈值（5 次）标记 FAILED；
 * 已 PROCESSED 事件靠查询条件天然跳过（幂等）。</p>
 *
 * <p>约定：inbox_event 无 payload 列，FileDeleted 信封的 {@code aggregateId=fileId}
 * （File 侧 FileEventPublisher 发布 FileDeleted 时 aggregateId 即 fileId，payload 的
 * fileId 与之同值）。退避计数为进程内状态，服务重启后未 FAILED 事件会从头重试，
 * 业务上幂等（置空操作可重复执行）。</p>
 */
@Component
public class FileDeletedInboxConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileDeletedInboxConsumer.class);
    private static final String EVENT_TYPE = "FileDeleted";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSED = "PROCESSED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String EFFECT_AVATAR_CLEARED = "AVATAR_CLEARED";
    private static final String EFFECT_NO_OP = "NO_OP";
    private static final String ERROR_CODE_PROCESS_FAILED = "FILE_DELETED_PROCESS_FAILED";
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;

    private final InboxEventMapper inboxEventMapper;
    private final UserProfileMapper userProfileMapper;
    private final Clock clock;
    /** 退避记账：eventId -> 尝试次数与下次可重试时刻（inbox_event 无退避列，进程内维护）。 */
    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public FileDeletedInboxConsumer(
            InboxEventMapper inboxEventMapper,
            UserProfileMapper userProfileMapper,
            Clock clock) {
        this.inboxEventMapper = inboxEventMapper;
        this.userProfileMapper = userProfileMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${educloud.user.inbox.poll-interval:5000}")
    @Transactional
    public void consumePending() {
        Instant now = clock.instant();
        List<InboxEventEntity> pending = inboxEventMapper.selectList(
                new QueryWrapper<InboxEventEntity>()
                        .eq("event_type", EVENT_TYPE)
                        .eq("process_status", STATUS_PENDING)
                        .orderByAsc("id")
                        .last("LIMIT " + BATCH_SIZE));
        if (pending == null) {
            return;
        }
        for (InboxEventEntity event : pending) {
            String eventId = event.getEventId();
            AttemptState state = attempts.get(eventId);
            if (state != null && state.nextAttemptAt().isAfter(now)) {
                continue;
            }
            try {
                Long fileId = fileIdOf(event);
                int cleared = userProfileMapper.update(
                        null,
                        new UpdateWrapper<UserProfileEntity>()
                                .eq("avatar_file_id", fileId)
                                .set("avatar_file_id", null));
                String effect = cleared > 0 ? EFFECT_AVATAR_CLEARED : EFFECT_NO_OP;
                inboxEventMapper.update(
                        null,
                        new UpdateWrapper<InboxEventEntity>()
                                .eq("id", event.getId())
                                .set("process_status", STATUS_PROCESSED)
                                .set("business_effect", effect)
                                .set("processed_at", now)
                                .set("error_code", null));
                attempts.remove(eventId);
                LOGGER.info(
                        "FileDeleted inbox event {} processed, fileId={}, effect={}",
                        eventId, fileId, effect);
            } catch (Exception failure) {
                int attempt = (state == null ? 0 : state.attempts()) + 1;
                boolean failed = attempt >= MAX_ATTEMPTS;
                if (failed) {
                    // B3 修复：达阈值置 FAILED 后移除退避记账，避免 attempts Map 随历史
                    // 事件无限增长；仅 PENDING 重试期间保留 entry（FAILED 事件不再被轮询，
                    // 也无须退避）。
                    attempts.remove(eventId);
                } else {
                    Instant nextAttemptAt = now.plusSeconds(Math.min(300L, 2L * attempt));
                    attempts.put(eventId, new AttemptState(attempt, nextAttemptAt));
                }
                inboxEventMapper.update(
                        null,
                        new UpdateWrapper<InboxEventEntity>()
                                .eq("id", event.getId())
                                .set("process_status", failed ? STATUS_FAILED : STATUS_PENDING)
                                .set("error_code", ERROR_CODE_PROCESS_FAILED));
                if (failed) {
                    LOGGER.error(
                            "FileDeleted inbox event {} reached the retry limit and is marked FAILED",
                            eventId, failure);
                } else {
                    LOGGER.warn(
                            "FileDeleted inbox event {} processing failed, attempt {}",
                            eventId, attempt, failure);
                }
            }
        }
    }

    /** inbox_event 无 payload 列：FileDeleted 信封约定 aggregateId=fileId。 */
    private static Long fileIdOf(InboxEventEntity event) {
        return Long.valueOf(event.getAggregateId());
    }

    private record AttemptState(int attempts, Instant nextAttemptAt) {
    }
}
