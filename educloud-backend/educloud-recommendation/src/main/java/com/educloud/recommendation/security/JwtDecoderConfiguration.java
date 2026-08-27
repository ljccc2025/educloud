package com.educloud.recommendation.security;

import com.educloud.recommendation.config.RecommendationProperties;
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
 * 推荐服务 JWT 解码配置（复制 educloud-course JwtDecoderConfiguration 适配推荐模块）。
 *
 * <p>启动时静态加载 User 公钥 JWKS（{@link JwksLoader}：classpath: 测试默认或 file:
 * 生产），RS256 + kid 选择；validator = 时间戳 + issuer + {@link
 * RecommendationJwtValidator}（aud 契约，audience 默认 educloud-web）。JWKS 缺失即
 * 启动失败（fail-fast，与 course/file 一致）。</p>
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
    Clock recommendationClock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtDecoder recommendationJwtDecoder(
            JwksLoader loader,
            RecommendationProperties properties,
            Clock clock,
            JwksState state) {
        JwksLoader.LoadedJwks loaded = loader.load(properties);
        // Spring Security 6.2 无 NimbusJwtDecoder.withJwkSource 静态方法：用
        // DefaultJWTProcessor + JWSVerificationKeySelector 消费本地 JWKSource
        // （与 course/file JwtDecoder bean 同构，支持多 kid 轮换）。
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(loaded.jwkSet());
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector(JWSAlgorithm.RS256, source));
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                timestampValidator,
                new JwtIssuerValidator(properties.getJwt().getIssuer()),
                new RecommendationJwtValidator(properties)));
        state.markLoaded(loaded.keyIds());
        return decoder;
    }
}
