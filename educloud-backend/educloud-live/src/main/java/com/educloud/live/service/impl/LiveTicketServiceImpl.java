package com.educloud.live.service.impl;

import com.educloud.live.config.LiveProperties;
import com.educloud.live.dto.response.LiveTicketResponse;
import com.educloud.live.entity.LiveRoomEntity;
import com.educloud.live.enums.LiveSenderRole;
import com.educloud.live.exception.LiveErrorCode;
import com.educloud.live.exception.LiveException;
import com.educloud.live.feign.CourseClient;
import com.educloud.live.feign.dto.CourseEnrollmentStatusResponse;
import com.educloud.live.mapper.LiveRoomMapper;
import com.educloud.live.service.LiveTicketService;
import com.educloud.live.websocket.model.LiveTicketPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveTicketServiceImpl implements LiveTicketService {

    private static final String TICKET_KEY_PREFIX = "educloud:live:ticket:";
    private static final long TICKET_TTL_SECONDS = 60L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final LiveRoomMapper liveRoomMapper;
    private final CourseClient courseClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final LiveProperties liveProperties;

    @Override
    public LiveTicketResponse issueConnectionTicket(
            Long roomId, Long currentUserId, String currentUserName, boolean isTeacherOrAdmin) {
        LiveRoomEntity room = liveRoomMapper.selectById(roomId);
        if (room == null || (room.getDeleted() != null && room.getDeleted() == 1)) {
            throw new LiveException(LiveErrorCode.LIVE_ROOM_NOT_FOUND);
        }

        LiveSenderRole role;
        if (isTeacherOrAdmin || (currentUserId != null && currentUserId.equals(room.getTeacherId()))) {
            role = LiveSenderRole.TEACHER;
        } else {
            role = LiveSenderRole.STUDENT;
            // 外部 Feign 校验学生选课状态（置于事务外）
            validateCourseEnrollment(room.getCourseId(), currentUserId);
        }

        String ticket = UUID.randomUUID().toString().replace("-", "");
        String redisKey = TICKET_KEY_PREFIX + ticket;

        LiveTicketPayload payload = LiveTicketPayload.builder()
                .roomId(roomId)
                .userId(currentUserId)
                .role(role)
                .nickname(currentUserName != null && !currentUserName.isBlank() ? currentUserName : "User_" + currentUserId)
                .issuedAt(LocalDateTime.now())
                .build();

        try {
            String json = OBJECT_MAPPER.writeValueAsString(payload);
            stringRedisTemplate.opsForValue().set(redisKey, json, Duration.ofSeconds(TICKET_TTL_SECONDS));
        } catch (Exception e) {
            log.error("Failed to serialize and store live ticket in Redis: ticket={}", ticket, e);
            throw new RuntimeException("生成长连接票据失败", e);
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(TICKET_TTL_SECONDS);
        String wsEndpoint = "/ws/v1/live/" + roomId + "?ticket=" + ticket;

        log.info("Issued live connection ticket: roomId={}, userId={}, role={}, ticket={}", roomId, currentUserId, role, ticket);

        return LiveTicketResponse.builder()
                .ticket(ticket)
                .roomId(roomId)
                .wsEndpoint(wsEndpoint)
                .expiresInSeconds(TICKET_TTL_SECONDS)
                .expiresAt(expiresAt)
                .build();
    }

    @Override
    public LiveTicketPayload verifyAndConsumeTicket(Long roomId, String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new LiveException(LiveErrorCode.LIVE_TICKET_EXPIRED_OR_INVALID, "缺少 WebSocket 票据参数");
        }

        String redisKey = TICKET_KEY_PREFIX + ticket;
        // 使用 Redis GETDEL 原子获取并删除
        String rawJson = stringRedisTemplate.opsForValue().getAndDelete(redisKey);
        if (rawJson == null || rawJson.isBlank()) {
            log.warn("Live ticket invalid or already consumed: ticket={}", ticket);
            throw new LiveException(LiveErrorCode.LIVE_TICKET_EXPIRED_OR_INVALID, "票据已过期或已被使用");
        }

        LiveTicketPayload payload;
        try {
            payload = OBJECT_MAPPER.readValue(rawJson, LiveTicketPayload.class);
        } catch (Exception e) {
            log.error("Failed to deserialize live ticket: json={}", rawJson, e);
            throw new LiveException(LiveErrorCode.LIVE_TICKET_EXPIRED_OR_INVALID, "票据解析失败");
        }

        if (payload.getRoomId() == null || !payload.getRoomId().equals(roomId)) {
            log.warn("Live ticket roomId mismatch: expected={}, actual={}", roomId, payload.getRoomId());
            throw new LiveException(LiveErrorCode.LIVE_TICKET_EXPIRED_OR_INVALID, "票据与当前直播间不匹配");
        }

        return payload;
    }

    private void validateCourseEnrollment(Long courseId, Long studentId) {
        try {
            String internalSecret = liveProperties.getInternal() != null ? liveProperties.getInternal().getSecretToken() : null;
            CourseEnrollmentStatusResponse response = courseClient.getEnrollmentStatus(
                    courseId, studentId, internalSecret, "educloud-live");

            if (response == null || !response.isEnrolled() || !"ACTIVE".equalsIgnoreCase(response.getStatus())) {
                log.warn("Student not actively enrolled in course: courseId={}, studentId={}, response={}",
                        courseId, studentId, response);
                throw new LiveException(LiveErrorCode.COURSE_NOT_ENROLLED);
            }
        } catch (LiveException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to query course enrollment status via Feign: courseId={}, studentId={}",
                    courseId, studentId, e);
            throw new LiveException(LiveErrorCode.COURSE_NOT_ENROLLED, "校验课程选课权益失败");
        }
    }
}
