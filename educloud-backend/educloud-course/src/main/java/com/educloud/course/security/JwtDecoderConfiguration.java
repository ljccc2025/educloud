package com.educloud.course.security;

import com.educloud.course.config.CourseProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Clock;
import java.time.Duration;

/**
 * Course 服务 JWT 解码配置（复制 file JwtDecoderConfiguration 适配 Course）。
 *
 * <p>启动时静态加载 User 公钥 JWKS（{@link JwksLoader}：classpath: 测试默认或 file: 生产），
 * RS256 + kid 选择；validator = 时间戳 + issuer + {@link CourseJwtValidator}（aud/claims 契约）。
 * 单个 JwtDecoder bean 同时服务 oauth2ResourceServer 与
 * {@link com.educloud.course.config.InternalApiFilter}；服务令牌在 CourseJwtValidator
 * 走宽松分支，aud/clientId 由 InternalApiFilter 把关。</p>
 */
@Configuration(proxyBeanMethods = false)
public class JwtDecoderConfiguration {

    @Bean
    JwksLoader jwksLoader() {
        return new JwksLoader();
    }

    @Bean
    JwksState jwksState() {
        return new JwksState();
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock courseClock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtDecoder courseJwtDecoder(
            JwksLoader loader,
            CourseProperties properties,
            Clock clock,
            JwksState state) {
        JwksLoader.LoadedJwks loaded = loader.load(properties);
        // Spring Security 6.2 无 NimbusJwtDecoder.withJwkSource 静态方法：用
        // DefaultJWTProcessor + JWSVerificationKeySelector 消费本地 JWKSource
        // （与 user/file JwtDecoder bean 同构，支持多 kid 轮换）。
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(loaded.jwkSet());
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector(JWSAlgorithm.RS256, source));
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                timestampValidator,
                new JwtIssuerValidator(properties.jwt().issuer()),
                new CourseJwtValidator(properties, clock)));
        state.markLoaded(loaded.keyIds());
        return decoder;
    }
}
