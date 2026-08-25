package com.educloud.notification.spi;

import com.educloud.notification.config.NotificationProperties;
import com.educloud.notification.spi.model.EmailSendContext;
import com.educloud.notification.spi.model.EmailSendResult;
import com.educloud.notification.spi.plugins.MockEmailPlugin;
import com.educloud.notification.spi.plugins.SmtpEmailPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailChannelPluginTest {

    @Test
    @DisplayName("Mock Email Plugin 发信测试")
    void testMockEmailPlugin() {
        MockEmailPlugin plugin = new MockEmailPlugin();
        assertThat(plugin.getProviderCode()).isEqualTo("mock");

        EmailSendContext context = EmailSendContext.builder()
                .to("student@educloud.cn")
                .subject("课程报名成功")
                .content("您已成功报名《微服务实战》")
                .html(false)
                .userId(1001L)
                .build();

        EmailSendResult result = plugin.sendEmail(context);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessageId()).startsWith("MOCK_MAIL_");
    }

    @Test
    @DisplayName("Email Channel Factory 获取插件测试")
    void testEmailChannelFactory() {
        NotificationProperties properties = new NotificationProperties();
        properties.getEmail().setProvider("mock");

        MockEmailPlugin mockPlugin = new MockEmailPlugin();
        SmtpEmailPlugin smtpPlugin = new SmtpEmailPlugin(properties);

        EmailChannelFactory factory = new EmailChannelFactory(List.of(mockPlugin, smtpPlugin), properties);

        EmailChannelPlugin defaultPlugin = factory.getDefaultPlugin();
        assertThat(defaultPlugin).isInstanceOf(MockEmailPlugin.class);

        EmailChannelPlugin smtp = factory.getPlugin("smtp");
        assertThat(smtp).isInstanceOf(SmtpEmailPlugin.class);

        EmailChannelPlugin fallback = factory.getPlugin("unknown_channel");
        assertThat(fallback).isInstanceOf(MockEmailPlugin.class);
    }
}
