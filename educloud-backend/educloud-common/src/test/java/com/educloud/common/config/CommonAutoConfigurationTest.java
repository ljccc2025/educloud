package com.educloud.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.common.id.RedisWorkerLeaseRepository;
import com.educloud.common.id.WorkerLeaseManager;
import com.educloud.common.security.SecurityContextFacade;
import com.educloud.common.web.GlobalExceptionHandler;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestContextFilter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class CommonAutoConfigurationTest {

    private static final AutoConfigurations COMMON_CONFIGURATIONS = AutoConfigurations.of(
            CommonCoreAutoConfiguration.class,
            CommonServletWebAutoConfiguration.class,
            CommonSecurityAutoConfiguration.class,
            CommonIdentifierAutoConfiguration.class);

    @Test
    void nonWebContextLoadsOnlyCoreCapabilities() {
        new ApplicationContextRunner()
                .withConfiguration(COMMON_CONFIGURATIONS)
                .run(context -> {
                    assertThat(context).hasSingleBean(Clock.class);
                    assertThat(context).hasSingleBean(JavaTimeModule.class);
                    assertThat(context).hasSingleBean(ApiResponseFactory.class);
                    assertThat(context).doesNotHaveBean(RequestContextFilter.class);
                    assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(SecurityContextFacade.class);
                });
    }

    @Test
    void servletContextLoadsWebAndSecurityCapabilities() {
        new WebApplicationContextRunner()
                .withConfiguration(COMMON_CONFIGURATIONS)
                .run(context -> {
                    assertThat(context).hasSingleBean(RequestContextFilter.class);
                    assertThat(context).hasSingleBean(RequestContextAccessor.class);
                    assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                    assertThat(context).hasSingleBean(SecurityContextFacade.class);
                });
    }

    @Test
    void reactiveContextNeverLoadsServletCapabilities() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(COMMON_CONFIGURATIONS)
                .run(context -> {
                    assertThat(context).hasSingleBean(Clock.class);
                    assertThat(context).doesNotHaveBean(RequestContextFilter.class);
                    assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(SecurityContextFacade.class);
                });
    }

    @Test
    void securityFacadeBacksOffWhenSpringSecurityIsAbsent() {
        new WebApplicationContextRunner()
                .withConfiguration(COMMON_CONFIGURATIONS)
                .withClassLoader(new FilteredClassLoader("org.springframework.security"))
                .run(context -> {
                    assertThat(context).hasSingleBean(RequestContextFilter.class);
                    assertThat(context).doesNotHaveBean(SecurityContextFacade.class);
                });
    }

    @Test
    void identifierChainRequiresBothEnablementAndRedis() {
        new ApplicationContextRunner()
                .withConfiguration(COMMON_CONFIGURATIONS)
                .withPropertyValues("educloud.common.id.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RedisWorkerLeaseRepository.class);
                    assertThat(context).doesNotHaveBean(WorkerLeaseManager.class);
                    assertThat(context).doesNotHaveBean(IdentifierGenerator.class);
                });

        new ApplicationContextRunner()
                .withConfiguration(COMMON_CONFIGURATIONS)
                .withUserConfiguration(RedisTestBeans.class)
                .run(context -> assertThat(context).doesNotHaveBean(WorkerLeaseManager.class));

        new ApplicationContextRunner()
                .withConfiguration(COMMON_CONFIGURATIONS)
                .withUserConfiguration(RedisTestBeans.class)
                .withPropertyValues("educloud.common.id.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisWorkerLeaseRepository.class);
                    assertThat(context).hasSingleBean(WorkerLeaseManager.class);
                    assertThat(context).hasSingleBean(IdentifierGenerator.class);
                });
    }

    @Test
    void userBeansOverrideEveryDefaultExtensionPoint() {
        new WebApplicationContextRunner()
                .withConfiguration(COMMON_CONFIGURATIONS)
                .withUserConfiguration(UserOverrides.class, RedisTestBeans.class)
                .withPropertyValues("educloud.common.id.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(Clock.class);
                    assertThat(context.getBean(Clock.class)).isSameAs(UserOverrides.CLOCK);
                    assertThat(context.getBean(RequestContextAccessor.class))
                            .isSameAs(UserOverrides.REQUEST_CONTEXT);
                    assertThat(context.getBean(SecurityContextFacade.class))
                            .isSameAs(UserOverrides.SECURITY_CONTEXT);
                    assertThat(context.getBean(IdentifierGenerator.class))
                            .isSameAs(UserOverrides.IDENTIFIER_GENERATOR);
                    assertThat(context).doesNotHaveBean(WorkerLeaseManager.class);
                    assertThat(context).doesNotHaveBean(RedisWorkerLeaseRepository.class);
                });
    }

    @Test
    void invalidPropertiesFailContextStartup() {
        assertInvalid("educloud.common.environment=");
        assertInvalid("educloud.common.id.lease-ttl=0s");
        assertInvalid("educloud.common.id.renewal-interval=30s");
        assertInvalid("educloud.common.id.clock-backward-tolerance=-1ms");
    }

    @Test
    void importsListsEveryAutoConfigurationExactlyOnce() throws IOException {
        String imports = new ClassPathResource(
                        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(imports.lines().filter(line -> !line.isBlank()).toList())
                .containsExactlyInAnyOrder(
                        CommonCoreAutoConfiguration.class.getName(),
                        CommonServletWebAutoConfiguration.class.getName(),
                        CommonSecurityAutoConfiguration.class.getName(),
                        CommonIdentifierAutoConfiguration.class.getName())
                .doesNotHaveDuplicates();
    }

    private static void assertInvalid(String property) {
        new ApplicationContextRunner()
                .withConfiguration(COMMON_CONFIGURATIONS)
                .withUserConfiguration(RedisTestBeans.class)
                .withPropertyValues("educloud.common.id.enabled=true", property)
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisTestBeans {

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return new ScriptedStringRedisTemplate();
        }
    }

    private static final class ScriptedStringRedisTemplate extends StringRedisTemplate {

        @Override
        public void afterPropertiesSet() {
            // This fixture exercises script protocol wiring without opening a Redis connection.
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            if (Long.class.equals(script.getResultType())) {
                return (T) Long.valueOf(1);
            }
            return (T) List.of(0L, 1_000L);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserOverrides {

        private static final Clock CLOCK =
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);
        private static final RequestContextAccessor REQUEST_CONTEXT = new RequestContextAccessor() {
            @Override
            public String requestId() {
                return "custom-request";
            }

            @Override
            public Optional<String> traceId() {
                return Optional.empty();
            }
        };
        private static final SecurityContextFacade SECURITY_CONTEXT = Optional::empty;
        private static final IdentifierGenerator IDENTIFIER_GENERATOR = () -> 42L;

        @Bean
        Clock customClock() {
            return CLOCK;
        }

        @Bean
        RequestContextAccessor customRequestContextAccessor() {
            return REQUEST_CONTEXT;
        }

        @Bean
        SecurityContextFacade customSecurityContextFacade() {
            return SECURITY_CONTEXT;
        }

        @Bean
        IdentifierGenerator customIdentifierGenerator() {
            return IDENTIFIER_GENERATOR;
        }
    }
}
