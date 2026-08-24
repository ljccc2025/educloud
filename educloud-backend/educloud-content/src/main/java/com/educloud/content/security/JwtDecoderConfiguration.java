package com.educloud.content.security;

import com.educloud.content.config.ContentProperties;
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
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Clock;
import java.time.Duration;

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
    Clock contentClock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtDecoder contentJwtDecoder(
            JwksLoader loader,
            ContentProperties properties,
            Clock clock,
            JwksState state) {
        JwksLoader.LoadedJwks loaded = loader.load(properties);
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(loaded.jwkSet());
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector(JWSAlgorithm.RS256, source));
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                timestampValidator,
                new ContentJwtValidator(properties, clock)));
        state.markLoaded(loaded.keyIds());
        return decoder;
    }
}
