package com.educloud.notification.service;

import com.educloud.notification.dto.request.EmailTestSendRequest;
import com.educloud.notification.dto.response.EmailChannelStatusResponse;

public interface EmailChannelService {

    EmailChannelStatusResponse getEmailChannelStatus();

    void testSendEmail(Long adminUserId, String adminEmail, EmailTestSendRequest request);
}
