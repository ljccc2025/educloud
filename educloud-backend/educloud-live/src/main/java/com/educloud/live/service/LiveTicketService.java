package com.educloud.live.service;

import com.educloud.live.dto.response.LiveTicketResponse;
import com.educloud.live.websocket.model.LiveTicketPayload;

public interface LiveTicketService {

    LiveTicketResponse issueConnectionTicket(Long roomId, Long currentUserId, String currentUserName, boolean isTeacherOrAdmin);

    LiveTicketPayload verifyAndConsumeTicket(Long roomId, String ticket);
}
