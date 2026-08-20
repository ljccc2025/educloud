package com.educloud.common.config;

import com.educloud.common.id.IdentifierGenerator;
import com.educloud.common.id.RedisWorkerLeaseRepository;
import com.educloud.common.id.WorkerLeaseIdentifierGenerator;
import com.educloud.common.id.WorkerLeaseManager;
import com.educloud.common.id.WorkerLeaseRepository;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(after = CommonCoreAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnProperty(prefix = "educloud.common.id", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CommonIdentifierProperties.class)
public class CommonIdentifierAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(StringRedisTemplate.class)
    static class RedisAvailableConfiguration {

        @Bean
        @ConditionalOnMissingBean(WorkerLeaseRepository.class)
        RedisWorkerLeaseRepository commonWorkerLeaseRepository(
                StringRedisTemplate redisTemplate) {
            return new RedisWorkerLeaseRepository(redisTemplate);
        }

        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean
        WorkerLeaseManager commonWorkerLeaseManager(
                WorkerLeaseRepository repository,
                CommonProperties commonProperties,
                CommonIdentifierProperties identifierProperties) {
            return new WorkerLeaseManager(
                    repository,
                    commonProperties.getEnvironment(),
                    identifierProperties.getLeaseTtl(),
                    identifierProperties.getRenewalInterval());
        }

        @Bean
        @ConditionalOnMissingBean(IdentifierGenerator.class)
        WorkerLeaseIdentifierGenerator commonIdentifierGenerator(
                WorkerLeaseManager leaseManager,
                Clock clock,
                CommonIdentifierProperties identifierProperties) {
            return new WorkerLeaseIdentifierGenerator(
                    leaseManager,
                    clock,
                    duration -> TimeUnit.NANOSECONDS.sleep(duration.toNanos()),
                    identifierProperties.getClockBackwardTolerance());
        }
    }
}
