package com.educloud.notification.service.impl;

import com.educloud.notification.config.NotificationProperties;
import com.educloud.notification.dto.request.EmailTestSendRequest;
import com.educloud.notification.dto.response.EmailChannelStatusResponse;
import com.educloud.notification.exception.NotificationBizException;
import com.educloud.notification.exception.NotificationErrorCode;
import com.educloud.notification.service.EmailChannelService;
import com.educloud.notification.spi.EmailChannelFactory;
import com.educloud.notification.spi.EmailChannelPlugin;
import com.educloud.notification.spi.model.EmailSendContext;
import com.educloud.notification.spi.model.EmailSendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailChannelServiceImpl implements EmailChannelService {

    private final NotificationProperties properties;
    private final EmailChannelFactory emailChannelFactory;
    private final StringRedisTemplate redisTemplate;

    private static final String RATE_LIMIT_PREFIX = "educloud:notification:email:rate:";
    private static final Duration RATE_LIMIT_DURATION = Duration.ofSeconds(60);

    @Override
    public EmailChannelStatusResponse getEmailChannelStatus() {
        NotificationProperties.EmailProperties emailProps = properties.getEmail();
        String rawUsername = emailProps.getUsername();
        String maskedUsername = maskEmail(rawUsername);
        boolean hasPassword = emailProps.getPassword() != null && !emailProps.getPassword().isBlank();

        return EmailChannelStatusResponse.builder()
                .provider(emailProps.getProvider())
                .host(emailProps.getHost())
                .port(emailProps.getPort())
                .username(maskedUsername)
                .from(emailProps.getFrom())
                .sslEnabled(emailProps.isSslEnabled())
                .passwordConfigured(hasPassword)
                .build();
    }

    @Override
    public void testSendEmail(Long adminUserId, String adminEmail, EmailTestSendRequest request) {
        if (adminUserId == null || adminEmail == null || adminEmail.isBlank()) {
            throw new NotificationBizException(NotificationErrorCode.INVALID_TARGET_USER, "无法获取管理员认证邮箱");
        }

        // 60s 频控防滥用
        String rateKey = RATE_LIMIT_PREFIX + adminUserId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(rateKey, "1", RATE_LIMIT_DURATION);
        if (Boolean.FALSE.equals(acquired)) {
            Long ttl = redisTemplate.getExpire(rateKey, TimeUnit.SECONDS);
            throw new NotificationBizException(
                    NotificationErrorCode.EMAIL_TEST_RATE_LIMITED,
                    "测试邮件发送过于频繁，请在 " + (ttl != null ? ttl : 60) + " 秒后重试"
            );
        }

        String subject = request != null && request.getCustomSubject() != null && !request.getCustomSubject().isBlank()
                ? request.getCustomSubject()
                : "EduCloud 平台邮件服务测试通知";
        String content = request != null && request.getCustomContent() != null && !request.getCustomContent().isBlank()
                ? request.getCustomContent()
                : "您好！这是一封来自 EduCloud 平台邮件通道的连通性自测邮件。如果您收到了此邮件，说明邮件服务配置正常。";

        EmailSendContext context = EmailSendContext.builder()
                .to(adminEmail)
                .subject(subject)
                .content(content)
                .html(false)
                .userId(adminUserId)
                .build();

        EmailChannelPlugin plugin = emailChannelFactory.getDefaultPlugin();
        EmailSendResult result = plugin.sendEmail(context);
        if (!result.isSuccess()) {
            log.error("Failed to send test email to {}: {}", adminEmail, result.getErrorMessage());
            throw new NotificationBizException(NotificationErrorCode.EMAIL_SEND_FAILED, result.getErrorMessage());
        }

        log.info("Admin {} successfully sent test email to {}, msgId={}",
                adminUserId, adminEmail, result.getMessageId());
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIdx = email.indexOf('@');
        String name = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        if (name.length() <= 3) {
            return name.charAt(0) + "***" + domain;
        }
        return name.substring(0, 3) + "***" + domain;
    }
}
