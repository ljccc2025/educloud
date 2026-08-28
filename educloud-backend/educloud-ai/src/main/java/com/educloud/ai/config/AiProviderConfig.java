package com.educloud.ai.config;

import com.educloud.ai.provider.ChatProvider;
import com.educloud.ai.provider.OpenAiCompatibleProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class AiProviderConfig {

    @Bean
    public ChatProvider chatProvider(RestClient.Builder builder, AiProperties properties) {
        String apiKey = properties.provider() != null ? properties.provider().apiKey() : null;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "AI_PROVIDER_API_KEY is empty; refusing to start (inject via deploy/docker-compose/.env)");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.timeout().connectMs());
        factory.setReadTimeout((int) properties.timeout().readMs());
        return new OpenAiCompatibleProvider(builder.requestFactory(factory), properties);
    }
}
