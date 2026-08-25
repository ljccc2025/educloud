package com.educloud.live.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.live.dto.request.LiveRoomCreateRequest;
import com.educloud.live.dto.request.LiveRoomPageQuery;
import com.educloud.live.dto.request.LiveRoomUpdateRequest;
import com.educloud.live.dto.response.LiveEndResponse;
import com.educloud.live.dto.response.LiveRoomDetailResponse;
import com.educloud.live.dto.response.LiveStartResponse;
import com.educloud.live.entity.LiveReplayEntity;
import com.educloud.live.entity.LiveRoomEntity;
import com.educloud.live.entity.LiveSessionEntity;
import com.educloud.live.enums.LiveProviderType;
import com.educloud.live.enums.LiveReplayStatus;
import com.educloud.live.enums.LiveRoomStatus;
import com.educloud.live.enums.LiveSessionStatus;
import com.educloud.live.exception.LiveErrorCode;
import com.educloud.live.exception.LiveException;
import com.educloud.live.mapper.LiveReplayMapper;
import com.educloud.live.mapper.LiveRoomMapper;
import com.educloud.live.mapper.LiveSessionMapper;
import com.educloud.live.service.LiveLifecycleService;
import com.educloud.live.spi.LiveStreamProvider;
import com.educloud.live.spi.LiveStreamProviderFactory;
import com.educloud.live.spi.model.LiveStreamPlayUrls;
import com.educloud.live.spi.model.LiveStreamPushUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveLifecycleServiceImpl implements LiveLifecycleService {

    private final LiveRoomMapper liveRoomMapper;
    private final LiveSessionMapper liveSessionMapper;
    private final LiveReplayMapper liveReplayMapper;
    private final LiveStreamProviderFactory providerFactory;
    private final IdentifierGenerator identifierGenerator;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public LiveRoomDetailResponse createRoom(Long currentUserId, boolean isAdmin, LiveRoomCreateRequest request) {
        if (!request.getScheduledEndAt().isAfter(request.getScheduledStartAt())) {
            throw new LiveException(LiveErrorCode.LIVE_ROOM_TIME_INVALID);
        }

        Long teacherId = isAdmin && request.getTeacherId() != null ? request.getTeacherId() : currentUserId;
        Long roomId = identifierGenerator.nextId();
        String streamKey = "live_" + roomId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        LiveRoomEntity entity = LiveRoomEntity.builder()
                .id(roomId)
                .courseId(request.getCourseId())
                .teacherId(teacherId)
                .title(request.getTitle())
                .description(request.getDescription())
                .scheduledStartAt(request.getScheduledStartAt())
                .scheduledEndAt(request.getScheduledEndAt())
                .status(LiveRoomStatus.CREATED)
                .providerType(LiveProviderType.MOCK)
                .streamKey(streamKey)
                .allowChat(true)
                .version(0L)
                .deleted(0)
                .build();

        liveRoomMapper.insert(entity);
        log.info("Live room created: id={}, courseId={}, teacherId={}", roomId, request.getCourseId(), teacherId);

        return convertToDetail(entity, 0, 0, null, null, null);
    }

    @Override
    @Transactional
    public LiveRoomDetailResponse updateRoom(Long roomId, Long currentUserId, boolean isAdmin, LiveRoomUpdateRequest request) {
        LiveRoomEntity room = getRoomOrThrow(roomId);
        checkTeacherOwnership(room, currentUserId, isAdmin);

        if (room.getStatus() == LiveRoomStatus.ENDED || room.getStatus() == LiveRoomStatus.CANCELLED) {
            throw new LiveException(LiveErrorCode.LIVE_ROOM_STATUS_INVALID, "已结课或已取消的直播间不能修改");
        }

        LocalDateTime start = request.getScheduledStartAt() != null ? request.getScheduledStartAt() : room.getScheduledStartAt();
        LocalDateTime end = request.getScheduledEndAt() != null ? request.getScheduledEndAt() : room.getScheduledEndAt();
        if (!end.isAfter(start)) {
            throw new LiveException(LiveErrorCode.LIVE_ROOM_TIME_INVALID);
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            room.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            room.setDescription(request.getDescription());
        }
        room.setScheduledStartAt(start);
        room.setScheduledEndAt(end);
        if (request.getAllowChat() != null) {
            room.setAllowChat(request.getAllowChat());
        }

        liveRoomMapper.updateById(room);
        return convertToDetail(room, 0, 0, null, null, null);
    }

    @Override
    public LiveStartResponse startLive(Long roomId, Long currentUserId, boolean isAdmin) {
        LiveRoomEntity room = getRoomOrThrow(roomId);
        checkTeacherOwnership(room, currentUserId, isAdmin);

        if (room.getStatus() != LiveRoomStatus.CREATED && room.getStatus() != LiveRoomStatus.LIVING) {
            throw new LiveException(LiveErrorCode.LIVE_ROOM_STATUS_INVALID, "直播间当前状态不允许开播: " + room.getStatus());
        }

        Long sessionId;
        if (room.getStatus() == LiveRoomStatus.CREATED) {
            int rows = liveRoomMapper.updateStatusCas(roomId, "CREATED", "LIVING");
            if (rows == 0) {
                throw new LiveException(LiveErrorCode.LIVE_ROOM_STATUS_INVALID, "开播失败，直播间状态已被并发修改");
            }
            room.setStatus(LiveRoomStatus.LIVING);

            sessionId = identifierGenerator.nextId();
            LiveSessionEntity session = LiveSessionEntity.builder()
                    .id(sessionId)
                    .roomId(roomId)
                    .sessionNo(1)
                    .status(LiveSessionStatus.LIVING)
                    .startedAt(LocalDateTime.now())
                    .startedBy(currentUserId)
                    .peakViewers(0)
                    .totalViewers(0)
                    .deleted(0)
                    .build();
            liveSessionMapper.insert(session);
        } else {
            // Already living, find current active session
            LiveSessionEntity activeSession = liveSessionMapper.selectOne(
                    new LambdaQueryWrapper<LiveSessionEntity>()
                            .eq(LiveSessionEntity::getRoomId, roomId)
                            .eq(LiveSessionEntity::getStatus, LiveSessionStatus.LIVING)
                            .last("LIMIT 1"));
            sessionId = activeSession != null ? activeSession.getId() : identifierGenerator.nextId();
        }

        LiveStreamProvider provider = providerFactory.getProvider(room.getProviderType());
        LiveStreamPushUrl pushInfo = provider.generatePushUrl(room);

        return LiveStartResponse.builder()
                .roomId(roomId)
                .sessionId(sessionId)
                .streamKey(room.getStreamKey())
                .pushInfo(pushInfo)
                .startedAt(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional
    public LiveEndResponse endLive(Long roomId, Long currentUserId, boolean isAdmin) {
        LiveRoomEntity room = getRoomOrThrow(roomId);
        checkTeacherOwnership(room, currentUserId, isAdmin);

        if (room.getStatus() != LiveRoomStatus.LIVING) {
            throw new LiveException(LiveErrorCode.LIVE_ROOM_STATUS_INVALID, "直播间未在直播中，无法下播: " + room.getStatus());
        }

        int rows = liveRoomMapper.updateStatusCas(roomId, "LIVING", "ENDED");
        if (rows == 0) {
            throw new LiveException(LiveErrorCode.LIVE_ROOM_STATUS_INVALID, "下播失败，状态已被并发修改");
        }
        room.setStatus(LiveRoomStatus.ENDED);

        LocalDateTime now = LocalDateTime.now();
        LiveSessionEntity activeSession = liveSessionMapper.selectOne(
                new LambdaQueryWrapper<LiveSessionEntity>()
                        .eq(LiveSessionEntity::getRoomId, roomId)
                        .eq(LiveSessionEntity::getStatus, LiveSessionStatus.LIVING)
                        .last("LIMIT 1"));

        Long sessionId = activeSession != null ? activeSession.getId() : roomId;
        long duration = activeSession != null ? Duration.between(activeSession.getStartedAt(), now).toSeconds() : 0;
        int peak = activeSession != null ? activeSession.getPeakViewers() : 0;
        int total = activeSession != null ? activeSession.getTotalViewers() : 0;

        if (activeSession != null) {
            liveSessionMapper.endSessionCas(activeSession.getId(), now, currentUserId, peak, Math.max(total, peak));
        }

        // 自动归档生成一条默认可播放的录制回放（绑定测试/默认 File ID）
        Long replayId = identifierGenerator.nextId();
        LiveReplayEntity replay = LiveReplayEntity.builder()
                .id(replayId)
                .roomId(roomId)
                .sessionId(sessionId)
                .fileId(9000000000000000001L)
                .title(room.getTitle() + " - 课堂回放录制")
                .durationSeconds(duration > 0 ? duration : 3600)
                .sizeBytes(104857600L)
                .status(LiveReplayStatus.AVAILABLE)
                .availableAt(now)
                .deleted(0)
                .build();
        liveReplayMapper.insert(replay);

        log.info("Live room ended: roomId={}, sessionId={}, durationSeconds={}, replayId={}", roomId, sessionId, duration, replayId);

        return LiveEndResponse.builder()
                .roomId(roomId)
                .sessionId(sessionId)
                .durationSeconds(duration)
                .peakViewers(peak)
                .totalViewers(total)
                .replayId(replayId)
                .endedAt(now)
                .build();
    }

    @Override
    @Transactional
    public void cancelRoom(Long roomId, Long currentUserId, boolean isAdmin) {
        LiveRoomEntity room = getRoomOrThrow(roomId);
        checkTeacherOwnership(room, currentUserId, isAdmin);

        if (room.getStatus() != LiveRoomStatus.CREATED) {
            throw new LiveException(LiveErrorCode.LIVE_ROOM_STATUS_INVALID, "只能取消未开播的直播间");
        }

        int rows = liveRoomMapper.updateStatusCas(roomId, "CREATED", "CANCELLED");
        if (rows == 0) {
            throw new LiveException(LiveErrorCode.LIVE_ROOM_STATUS_INVALID, "取消直播间失败，状态已变更");
        }
    }

    @Override
    public LiveRoomDetailResponse getRoomDetail(Long roomId, Long currentUserId, boolean isTeacherOrAdmin) {
        LiveRoomEntity room = getRoomOrThrow(roomId);

        int onlineCount = 0;
        try {
            Long count = stringRedisTemplate.opsForSet().size("educloud:live:room:" + roomId + ":online_users");
            if (count != null) {
                onlineCount = count.intValue();
            }
        } catch (Exception ignored) {
        }

        LiveSessionEntity activeSession = liveSessionMapper.selectOne(
                new LambdaQueryWrapper<LiveSessionEntity>()
                        .eq(LiveSessionEntity::getRoomId, roomId)
                        .eq(LiveSessionEntity::getStatus, LiveSessionStatus.LIVING)
                        .last("LIMIT 1"));

        Long sessionId = activeSession != null ? activeSession.getId() : null;
        int peakViewers = activeSession != null ? activeSession.getPeakViewers() : 0;

        LiveStreamProvider provider = providerFactory.getProvider(room.getProviderType());
        LiveStreamPlayUrls playInfo = provider.generatePlayUrls(room);
        LiveStreamPushUrl pushInfo = null;

        if (isTeacherOrAdmin || (currentUserId != null && currentUserId.equals(room.getTeacherId()))) {
            pushInfo = provider.generatePushUrl(room);
        }

        return convertToDetail(room, onlineCount, peakViewers, sessionId, pushInfo, playInfo);
    }

    @Override
    public Page<LiveRoomDetailResponse> listRooms(LiveRoomPageQuery query, Long currentUserId) {
        Page<LiveRoomEntity> page = new Page<>(query.getSafePage(), query.getSafeSize());
        LambdaQueryWrapper<LiveRoomEntity> wrapper = new LambdaQueryWrapper<LiveRoomEntity>()
                .eq(query.getCourseId() != null, LiveRoomEntity::getCourseId, query.getCourseId())
                .eq(query.getTeacherId() != null, LiveRoomEntity::getTeacherId, query.getTeacherId())
                .eq(query.getStatus() != null, LiveRoomEntity::getStatus, query.getStatus())
                .orderByDesc(LiveRoomEntity::getScheduledStartAt);

        Page<LiveRoomEntity> entityPage = liveRoomMapper.selectPage(page, wrapper);

        Page<LiveRoomDetailResponse> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        List<LiveRoomDetailResponse> records = entityPage.getRecords().stream()
                .map(room -> convertToDetail(room, 0, 0, null, null, null))
                .toList();
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    @Transactional
    public void toggleMute(Long roomId, Long currentUserId, boolean isTeacherOrAdmin, Boolean allowChat) {
        LiveRoomEntity room = getRoomOrThrow(roomId);
        if (!isTeacherOrAdmin && (currentUserId == null || !currentUserId.equals(room.getTeacherId()))) {
            throw new LiveException(LiveErrorCode.LIVE_COURSE_NOT_OWNED, "非主讲教师或管理员无法设置禁言");
        }

        boolean targetChat = Boolean.TRUE.equals(allowChat);
        liveRoomMapper.updateAllowChat(roomId, targetChat);
        log.info("Live room mute status updated: roomId={}, allowChat={}", roomId, targetChat);
    }

    private LiveRoomEntity getRoomOrThrow(Long roomId) {
        LiveRoomEntity room = liveRoomMapper.selectById(roomId);
        if (room == null || room.getDeleted() != null && room.getDeleted() == 1) {
            throw new LiveException(LiveErrorCode.LIVE_ROOM_NOT_FOUND);
        }
        return room;
    }

    private void checkTeacherOwnership(LiveRoomEntity room, Long currentUserId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (currentUserId == null || !currentUserId.equals(room.getTeacherId())) {
            throw new LiveException(LiveErrorCode.LIVE_COURSE_NOT_OWNED);
        }
    }

    private LiveRoomDetailResponse convertToDetail(
            LiveRoomEntity room,
            int onlineCount,
            int peakViewers,
            Long sessionId,
            LiveStreamPushUrl pushInfo,
            LiveStreamPlayUrls playInfo) {
        return LiveRoomDetailResponse.builder()
                .id(room.getId())
                .courseId(room.getCourseId())
                .teacherId(room.getTeacherId())
                .title(room.getTitle())
                .description(room.getDescription())
                .scheduledStartAt(room.getScheduledStartAt())
                .scheduledEndAt(room.getScheduledEndAt())
                .status(room.getStatus())
                .providerType(room.getProviderType())
                .streamKey(room.getStreamKey())
                .allowChat(room.getAllowChat())
                .currentOnlineViewers(onlineCount)
                .peakViewers(peakViewers)
                .currentSessionId(sessionId)
                .pushInfo(pushInfo)
                .playInfo(playInfo)
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }
}
