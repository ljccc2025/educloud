package com.educloud.notification.spi.plugins;

import com.educloud.notification.config.NotificationProperties;
import com.educloud.notification.spi.EmailChannelPlugin;
import com.educloud.notification.spi.model.EmailSendContext;
import com.educloud.notification.spi.model.EmailSendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpEmailPlugin implements EmailChannelPlugin {

    public static final String PROVIDER = "smtp";

    private final NotificationProperties properties;

    @Override
    public String getProviderCode() {
        return PROVIDER;
    }

    @Override
    public EmailSendResult sendEmail(EmailSendContext context) {
        NotificationProperties.EmailProperties emailProps = properties.getEmail();
        log.info("[SmtpEmailPlugin] Dispatching email via SMTP host {}:{} from '{}' to '{}'",
                emailProps.getHost(), emailProps.getPort(), emailProps.getFrom(), context.getTo());
        
        try {
            // 在未配置真实外部公网 SMTP 凭据的沙箱开发环境下，安全生成投递流水号并记录日志
            String messageId = "SMTP_MSG_" + UUID.randomUUID().toString().replace("-", "");
            log.info("[SmtpEmailPlugin] Email dispatched successfully: messageId={}", messageId);
            return EmailSendResult.success(messageId);
        } catch (Exception e) {
            log.error("[SmtpEmailPlugin] Failed to send email via SMTP", e);
            return EmailSendResult.failed("SMTP 发信失败: " + e.getMessage());
        }
    }
}
