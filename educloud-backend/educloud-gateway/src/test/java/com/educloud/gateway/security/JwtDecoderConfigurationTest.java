package com.educloud.gateway.security;

import com.educloud.gateway.config.GatewaySecurityProperties;
import com.nimbusds.jose.JWSAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtDecoderConfigurationTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void verifiesTokensFromBothConfiguredPublicKeysAndMarksStateLoaded() {
        TestJwtKeys first = new TestJwtKeys();
        TestJwtKeys second = new TestJwtKeys();
        JwksState state = new JwksState();
        ReactiveJwtDecoder decoder = decoder(TestJwtKeys.publicJwksJson(first, second), state);

        assertThat(decoder.decode(first.signedToken(validClaims())).block().getSubject()).isEqualTo("user:1001");
        assertThat(decoder.decode(second.signedToken(validClaims())).block().getSubject()).isEqualTo("user:1001");
        assertThat(state.loaded()).isTrue();
        assertThat(state.keyCount()).isEqualTo(2);
    }

    @Test
    void rejectsUnknownKidWrongAlgorithmUnsignedAndTamperedTokens() {
        TestJwtKeys trusted = new TestJwtKeys();
        TestJwtKeys untrusted = new TestJwtKeys();
        ReactiveJwtDecoder decoder = decoder(trusted.publicJwksJson(), new JwksState());

        String unknownKid = trusted.signedToken(validClaims(), JWSAlgorithm.RS256, "unknown-kid");
        assertDecodeRejected(decoder, unknownKid);

        String missingKid = trusted.signedToken(validClaims(), JWSAlgorithm.RS256, null);
        assertDecodeRejected(decoder, missingKid);

        String blankKid = trusted.signedToken(validClaims(), JWSAlgorithm.RS256, " ");
        assertDecodeRejected(decoder, blankKid);

        String wrongAlgorithm = trusted.signedToken(validClaims(), JWSAlgorithm.RS512, trusted.keyId());
        assertDecodeRejected(decoder, wrongAlgorithm);

        assertDecodeRejected(decoder, trusted.unsignedToken(validClaims()));

        String wrongSignature = untrusted.signedToken(validClaims(), JWSAlgorithm.RS256, trusted.keyId());
        assertDecodeRejected(decoder, wrongSignature);
    }

    @Test
    void rejectsAValidSignatureWhenTheStrictClaimContractFails() {
        TestJwtKeys trusted = new TestJwtKeys();
        ReactiveJwtDecoder decoder = decoder(trusted.publicJwksJson(), new JwksState());
        Map<String, Object> claims = validClaims();
        claims.put("aud", List.of("another-api"));

        assertDecodeRejected(decoder, trusted.signedToken(claims));
    }

    private static ReactiveJwtDecoder decoder(String jwksJson, JwksState state) {
        JwtDecoderConfiguration configuration = new JwtDecoderConfiguration();
        return configuration.gatewayJwtDecoder(new JwksLoader(), properties(jwksJson), CLOCK, state);
    }

    private static GatewaySecurityProperties properties(String jwksJson) {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.setJwksJson(jwksJson);
        properties.setIssuer("https://identity.educloud.local");
        properties.setAudience("educloud-api");
        properties.setClockSkew(Duration.ofSeconds(30));
        return properties;
    }

    private static Map<String, Object> validClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "https://identity.educloud.local");
        claims.put("aud", List.of("educloud-api"));
        claims.put("exp", NOW.plusSeconds(300));
        claims.put("nbf", NOW.minusSeconds(1));
        claims.put("iat", NOW.minusSeconds(1));
        claims.put("sub", "user:1001");
        claims.put("sid", "session-1001");
        claims.put("tokenVersion", 1L);
        claims.put("userType", "STUDENT");
        claims.put("roles", List.of("STUDENT"));
        claims.put("permissions", List.of("course:read"));
        return claims;
    }

    private static void assertDecodeRejected(ReactiveJwtDecoder decoder, String token) {
        assertThatThrownBy(() -> decoder.decode(token).block()).isInstanceOf(RuntimeException.class);
    }
}
