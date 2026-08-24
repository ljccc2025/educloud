package com.educloud.order.config;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.educloud.common.id.IdentifierGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdentifierConfig {

    @Bean
    @ConditionalOnMissingBean(IdentifierGenerator.class)
    public IdentifierGenerator identifierGenerator() {
        return IdWorker::getId;
    }
}
