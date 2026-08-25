package com.educloud.notification.spi;

import com.educloud.notification.spi.model.EmailSendContext;
import com.educloud.notification.spi.model.EmailSendResult;

public interface EmailChannelPlugin {

    String getProviderCode();

    EmailSendResult sendEmail(EmailSendContext context);
}
