package com.educloud.live.service;

import com.educloud.live.entity.LiveMessageEntity;
import com.educloud.live.enums.LiveMessageType;
import com.educloud.live.enums.LiveSenderRole;

import java.util.List;

public interface LiveMessageService {

    LiveMessageEntity saveMessage(Long roomId, Long currentUserId, String senderName, LiveSenderRole role, LiveMessageType type, String content);

    void recallMessage(Long messageId, Long currentUserId, boolean isTeacherOrAdmin);

    List<LiveMessageEntity> listMessages(Long roomId, Integer limit);
}
