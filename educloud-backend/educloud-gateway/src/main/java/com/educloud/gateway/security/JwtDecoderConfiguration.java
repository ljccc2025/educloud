package com.educloud.gateway.security;

import com.educloud.gateway.config.GatewaySecurityProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.util.function.Function;

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
    Clock gatewayClock() {
        return Clock.systemUTC();
    }

    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(
            JwksLoader loader,
            GatewaySecurityProperties properties,
            Clock clock,
            JwksState state) {
        JwksLoader.LoadedJwks loaded = loader.load(properties);
        Function<SignedJWT, Flux<JWK>> source = signed -> {
            if (!JWSAlgorithm.RS256.equals(signed.getHeader().getAlgorithm())
                    || !StringUtils.hasText(signed.getHeader().getKeyID())
                    || !loaded.keyIds().contains(signed.getHeader().getKeyID())) {
                return Flux.empty();
            }
            return Flux.fromIterable(new JWKSelector(JWKMatcher.forJWSHeader(signed.getHeader()))
                    .select(loaded.jwkSet()));
        };
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSource(source)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(properties.getClockSkew());
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                timestampValidator,
                new JwtIssuerValidator(properties.getIssuer()),
                new GatewayJwtValidator(properties, clock)));
        state.markLoaded(loaded.keyIds());
        return decoder;
    }
}
