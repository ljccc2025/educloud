package com.educloud.live.security;

import com.educloud.live.config.LiveProperties;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
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
    Clock liveClock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtDecoder liveJwtDecoder(
            JwksLoader loader,
            LiveProperties properties,
            Clock clock,
            JwksState state) {
        JwksLoader.LoadedJwks loaded = loader.load(properties);
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(loaded.jwkSet());
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestampValidator.setClock(clock);
        String issuer = properties.getJwt() != null ? properties.getJwt().getIssuer() : "https://issuer.educloud.local";
        JwtIssuerValidator issuerValidator = new JwtIssuerValidator(issuer);
        LiveJwtValidator liveValidator = new LiveJwtValidator(properties, clock);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                issuerValidator,
                liveValidator));
        state.updateLoadedTime();
        return decoder;
    }
}
