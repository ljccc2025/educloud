package com.educloud.notification.spi.plugins;

import com.educloud.notification.spi.EmailChannelPlugin;
import com.educloud.notification.spi.model.EmailSendContext;
import com.educloud.notification.spi.model.EmailSendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class MockEmailPlugin implements EmailChannelPlugin {

    public static final String PROVIDER = "mock";

    @Override
    public String getProviderCode() {
        return PROVIDER;
    }

    @Override
    public EmailSendResult sendEmail(EmailSendContext context) {
        String msgId = "MOCK_MAIL_" + UUID.randomUUID().toString().replace("-", "");
        log.info("[MockEmailPlugin] Simulated email dispatch: to={}, subject={}, msgId={}",
                context.getTo(), context.getSubject(), msgId);
        return EmailSendResult.success(msgId);
    }
}
