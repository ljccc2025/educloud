package com.educloud.live.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.live.config.LiveProperties;
import com.educloud.live.dto.response.LiveReplayResponse;
import com.educloud.live.entity.LiveReplayEntity;
import com.educloud.live.entity.LiveRoomEntity;
import com.educloud.live.enums.LiveReplayStatus;
import com.educloud.live.exception.LiveErrorCode;
import com.educloud.live.exception.LiveException;
import com.educloud.live.feign.CourseClient;
import com.educloud.live.feign.FileClient;
import com.educloud.live.feign.dto.CourseEnrollmentStatusResponse;
import com.educloud.live.feign.dto.DownloadGrantRequest;
import com.educloud.live.feign.dto.DownloadGrantResponse;
import com.educloud.live.mapper.LiveReplayMapper;
import com.educloud.live.mapper.LiveRoomMapper;
import com.educloud.live.service.LiveReplayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveReplayServiceImpl implements LiveReplayService {

    private final LiveReplayMapper liveReplayMapper;
    private final LiveRoomMapper liveRoomMapper;
    private final CourseClient courseClient;
    private final FileClient fileClient;
    private final LiveProperties liveProperties;

    @Override
    public LiveReplayResponse getReplayDetail(Long replayId, Long currentUserId, boolean isTeacherOrAdmin) {
        LiveReplayEntity replay = liveReplayMapper.selectById(replayId);
        if (replay == null || (replay.getDeleted() != null && replay.getDeleted() == 1)
                || replay.getStatus() == LiveReplayStatus.DELETED) {
            throw new LiveException(LiveErrorCode.LIVE_REPLAY_NOT_FOUND);
        }

        LiveRoomEntity room = liveRoomMapper.selectById(replay.getRoomId());
        if (room == null) {
            throw new LiveException(LiveErrorCode.LIVE_ROOM_NOT_FOUND);
        }

        if (!isTeacherOrAdmin && (currentUserId == null || !currentUserId.equals(room.getTeacherId()))) {
            validateCourseEnrollment(room.getCourseId(), currentUserId);
        }

        String playUrl = generatePlayUrl(replay.getFileId(), currentUserId, replay.getRoomId());

        return convertToResponse(replay, playUrl);
    }

    @Override
    public List<LiveReplayResponse> listReplaysByRoom(Long roomId, Long currentUserId, boolean isTeacherOrAdmin) {
        LiveRoomEntity room = liveRoomMapper.selectById(roomId);
        if (room == null || (room.getDeleted() != null && room.getDeleted() == 1)) {
            throw new LiveException(LiveErrorCode.LIVE_ROOM_NOT_FOUND);
        }

        if (!isTeacherOrAdmin && (currentUserId == null || !currentUserId.equals(room.getTeacherId()))) {
            validateCourseEnrollment(room.getCourseId(), currentUserId);
        }

        List<LiveReplayEntity> list = liveReplayMapper.selectList(
                new LambdaQueryWrapper<LiveReplayEntity>()
                        .eq(LiveReplayEntity::getRoomId, roomId)
                        .ne(LiveReplayEntity::getStatus, LiveReplayStatus.DELETED)
                        .orderByDesc(LiveReplayEntity::getCreatedAt));

        return list.stream()
                .map(replay -> convertToResponse(replay, generatePlayUrl(replay.getFileId(), currentUserId, roomId)))
                .toList();
    }

    @Override
    @Transactional
    public void deleteReplay(Long replayId, Long currentUserId, boolean isAdmin) {
        LiveReplayEntity replay = liveReplayMapper.selectById(replayId);
        if (replay == null || (replay.getDeleted() != null && replay.getDeleted() == 1)) {
            throw new LiveException(LiveErrorCode.LIVE_REPLAY_NOT_FOUND);
        }

        LiveRoomEntity room = liveRoomMapper.selectById(replay.getRoomId());
        if (room != null && !isAdmin && (currentUserId == null || !currentUserId.equals(room.getTeacherId()))) {
            throw new LiveException(LiveErrorCode.LIVE_COURSE_NOT_OWNED, "无权删除该回放");
        }

        replay.setStatus(LiveReplayStatus.DELETED);
        liveReplayMapper.updateById(replay);
        liveReplayMapper.deleteById(replayId);
    }

    private void validateCourseEnrollment(Long courseId, Long studentId) {
        if (studentId == null) {
            throw new LiveException(LiveErrorCode.COURSE_NOT_ENROLLED);
        }
        try {
            String internalSecret = liveProperties.getInternal() != null ? liveProperties.getInternal().getSecretToken() : null;
            CourseEnrollmentStatusResponse response = courseClient.getEnrollmentStatus(
                    courseId, studentId, internalSecret, "educloud-live");

            if (response == null || !response.isEnrolled() || !"ACTIVE".equalsIgnoreCase(response.getStatus())) {
                throw new LiveException(LiveErrorCode.COURSE_NOT_ENROLLED);
            }
        } catch (LiveException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to check course enrollment for replay: courseId={}, studentId={}", courseId, studentId, e);
            throw new LiveException(LiveErrorCode.COURSE_NOT_ENROLLED, "选课权限校验失败");
        }
    }

    private String generatePlayUrl(Long fileId, Long userId, Long roomId) {
        if (fileId == null) {
            return null;
        }
        try {
            String internalSecret = liveProperties.getInternal() != null ? liveProperties.getInternal().getSecretToken() : null;
            DownloadGrantRequest request = DownloadGrantRequest.builder()
                    .subjectType("USER")
                    .subjectUserId(userId)
                    .ownerType("LIVE_ROOM")
                    .ownerId(String.valueOf(roomId))
                    .purpose("LIVE_REPLAY")
                    .requestedTtlSeconds(7200L)
                    .build();

            DownloadGrantResponse response = fileClient.grantSingle(fileId, request, internalSecret, "educloud-live");
            if (response != null && response.getDownloadUrl() != null) {
                return response.getDownloadUrl();
            }
        } catch (Exception e) {
            log.warn("Failed to get signed download grant from File service for fileId={}, using fallback: {}", fileId, e.getMessage());
        }
        return "/api/v1/files/" + fileId + "/content?auth=live_replay_token";
    }

    private LiveReplayResponse convertToResponse(LiveReplayEntity replay, String playUrl) {
        return LiveReplayResponse.builder()
                .id(replay.getId())
                .roomId(replay.getRoomId())
                .sessionId(replay.getSessionId())
                .fileId(replay.getFileId())
                .title(replay.getTitle())
                .durationSeconds(replay.getDurationSeconds())
                .sizeBytes(replay.getSizeBytes())
                .status(replay.getStatus())
                .playUrl(playUrl)
                .availableAt(replay.getAvailableAt())
                .createdAt(replay.getCreatedAt())
                .build();
    }
}
