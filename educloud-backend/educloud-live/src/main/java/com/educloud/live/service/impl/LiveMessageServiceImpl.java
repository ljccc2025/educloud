package com.educloud.live.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.live.entity.LiveMessageEntity;
import com.educloud.live.entity.LiveSessionEntity;
import com.educloud.live.enums.LiveMessageStatus;
import com.educloud.live.enums.LiveMessageType;
import com.educloud.live.enums.LiveSenderRole;
import com.educloud.live.enums.LiveSessionStatus;
import com.educloud.live.exception.LiveErrorCode;
import com.educloud.live.exception.LiveException;
import com.educloud.live.mapper.LiveMessageMapper;
import com.educloud.live.mapper.LiveSessionMapper;
import com.educloud.live.service.LiveMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveMessageServiceImpl implements LiveMessageService {

    private final LiveMessageMapper liveMessageMapper;
    private final LiveSessionMapper liveSessionMapper;
    private final IdentifierGenerator identifierGenerator;

    @Override
    @Transactional
    public LiveMessageEntity saveMessage(
            Long roomId, Long currentUserId, String senderName, LiveSenderRole role, LiveMessageType type, String content) {
        LiveSessionEntity activeSession = liveSessionMapper.selectOne(
                new LambdaQueryWrapper<LiveSessionEntity>()
                        .eq(LiveSessionEntity::getRoomId, roomId)
                        .eq(LiveSessionEntity::getStatus, LiveSessionStatus.LIVING)
                        .last("LIMIT 1"));

        Long sessionId = activeSession != null ? activeSession.getId() : roomId;
        Long messageId = identifierGenerator.nextId();

        LiveMessageEntity entity = LiveMessageEntity.builder()
                .id(messageId)
                .roomId(roomId)
                .sessionId(sessionId)
                .senderId(currentUserId)
                .senderName(senderName != null ? senderName : "User_" + currentUserId)
                .senderRole(role != null ? role : LiveSenderRole.STUDENT)
                .messageType(type != null ? type : LiveMessageType.CHAT)
                .content(content != null ? content : "")
                .status(LiveMessageStatus.NORMAL)
                .sentAt(LocalDateTime.now())
                .build();

        liveMessageMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public void recallMessage(Long messageId, Long currentUserId, boolean isTeacherOrAdmin) {
        LiveMessageEntity message = liveMessageMapper.selectById(messageId);
        if (message == null) {
            throw new LiveException(LiveErrorCode.LIVE_MESSAGE_NOT_FOUND);
        }

        if (!isTeacherOrAdmin) {
            if (currentUserId == null || !currentUserId.equals(message.getSenderId())) {
                throw new LiveException(LiveErrorCode.LIVE_MESSAGE_RECALL_FORBIDDEN, "无权撤回他人发言");
            }
            if (message.getSentAt() != null
                    && Duration.between(message.getSentAt(), LocalDateTime.now()).toMinutes() > 2) {
                throw new LiveException(LiveErrorCode.LIVE_MESSAGE_RECALL_FORBIDDEN, "已超过2分钟消息撤回时限");
            }
        }

        liveMessageMapper.recallMessage(messageId, LocalDateTime.now(), currentUserId);
        log.info("Live message recalled: messageId={}, recalledBy={}", messageId, currentUserId);
    }

    @Override
    public List<LiveMessageEntity> listMessages(Long roomId, Integer limit) {
        int safeLimit = limit != null && limit > 0 ? Math.min(limit, 100) : 50;
        return liveMessageMapper.selectList(
                new LambdaQueryWrapper<LiveMessageEntity>()
                        .eq(LiveMessageEntity::getRoomId, roomId)
                        .orderByDesc(LiveMessageEntity::getSentAt)
                        .last("LIMIT " + safeLimit));
    }
}
