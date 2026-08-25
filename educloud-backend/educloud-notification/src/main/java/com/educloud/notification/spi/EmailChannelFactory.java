package com.educloud.notification.spi;

import com.educloud.notification.config.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class EmailChannelFactory {

    private final Map<String, EmailChannelPlugin> pluginMap = new ConcurrentHashMap<>();
    private final NotificationProperties properties;

    public EmailChannelFactory(List<EmailChannelPlugin> plugins, NotificationProperties properties) {
        this.properties = properties;
        for (EmailChannelPlugin plugin : plugins) {
            pluginMap.put(plugin.getProviderCode().toLowerCase(Locale.ROOT), plugin);
        }
    }

    public EmailChannelPlugin getPlugin(String providerCode) {
        String code = (providerCode != null ? providerCode : properties.getEmail().getProvider())
                .toLowerCase(Locale.ROOT);
        EmailChannelPlugin plugin = pluginMap.get(code);
        if (plugin == null) {
            log.warn("Email provider '{}' not found, falling back to mock provider", code);
            plugin = pluginMap.get("mock");
        }
        return plugin;
    }

    public EmailChannelPlugin getDefaultPlugin() {
        return getPlugin(properties.getEmail().getProvider());
    }
}
