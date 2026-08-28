package com.educloud.ai.config;

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
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class JwtDecoderConfiguration {

    @Bean
    JwksLoader aiJwksLoader() {
        return new JwksLoader();
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock aiClock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtDecoder aiJwtDecoder(JwksLoader loader, AiProperties properties) {
        JwksLoader.LoadedJwks loaded = loader.load(properties);
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(loaded.jwkSet());
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);

        AiProperties.JwtProperties jwt = properties.jwt();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator(Duration.ofSeconds(30)));
        validators.add(new JwtIssuerValidator(
                jwt != null && jwt.issuer() != null ? jwt.issuer() : "https://issuer.educloud.local"));
        // Spring Security 6.2 无 JwtAudienceValidator（6.4 才引入），用语义等价的 JwtClaimValidator 校验 aud 包含目标值
        validators.add(new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(
                        jwt != null && jwt.audience() != null ? jwt.audience() : "educloud-api")));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }
}
