package com.educloud.order.security;

import com.educloud.order.config.OrderProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class JwksLoader {

    private static final int MAX_JWKS_BYTES = 256 * 1024;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> PRIVATE_RSA_PARAMETERS = Set.of(
            "d", "p", "q", "dp", "dq", "qi", "oth");

    public LoadedJwks load(OrderProperties properties) {
        if (properties == null || properties.jwt() == null) {
            throw invalid("educloud.order.jwt must be configured");
        }
        String location = properties.jwt().jwksLocation();
        if (!StringUtils.hasText(location)) {
            throw invalid("educloud.order.jwt.jwks-location must be configured");
        }
        String json = readResource(location);
        rejectPrivateParameters(json);
        JWKSet parsed;
        try {
            parsed = JWKSet.parse(json);
        } catch (ParseException | RuntimeException exception) {
            throw invalid("invalid JWKS syntax", exception);
        }
        if (parsed.getKeys().isEmpty()) {
            throw invalid("JWKS must contain at least one public key");
        }

        Set<String> keyIds = new HashSet<>();
        List<JWK> publicKeys = new ArrayList<>();
        for (JWK key : parsed.getKeys()) {
            validateKey(key, keyIds);
            publicKeys.add(key.toPublicJWK());
        }
        return new LoadedJwks(new JWKSet(List.copyOf(publicKeys)), Set.copyOf(keyIds));
    }

    private static void rejectPrivateParameters(String json) {
        try {
            JsonNode keys = OBJECT_MAPPER.readTree(json).path("keys");
            if (keys.isArray()) {
                for (JsonNode key : keys) {
                    if (PRIVATE_RSA_PARAMETERS.stream().anyMatch(key::has)) {
                        throw invalid("JWKS must not contain private key parameters");
                    }
                }
            }
        } catch (JsonProcessingException exception) {
            throw invalid("invalid JWKS syntax", exception);
        }
    }

    private static String readResource(String location) {
        Resource resource;
        if (location.startsWith("classpath:")) {
            resource = new ClassPathResource(location.substring("classpath:".length()));
        } else if (location.startsWith("file:")) {
            resource = new FileSystemResource(location.substring("file:".length()));
        } else {
            resource = new FileSystemResource(location);
        }
        try {
            if (!resource.exists() || !resource.isReadable()) {
                throw invalid("educloud.order.jwt.jwks-location must be a readable resource: " + location);
            }
            if (resource.contentLength() > MAX_JWKS_BYTES) {
                throw invalid("educloud.order.jwt.jwks-location exceeds 256 KiB");
            }
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw invalid("failed to read JWKS from " + location, exception);
        }
    }

    private static void validateKey(JWK key, Set<String> keyIds) {
        if (key.getKeyType() != KeyType.RSA || !(key instanceof RSAKey rsaKey)) {
            throw invalid("only RSA keys are supported, got " + key.getKeyType());
        }
        if (rsaKey.isPrivate()) {
            throw invalid("JWKS must not contain private keys");
        }
        String keyId = key.getKeyID();
        if (!StringUtils.hasText(keyId)) {
            throw invalid("all JWKs must have a non-empty kid");
        }
        if (!keyIds.add(keyId)) {
            throw invalid("duplicate kid in JWKS: " + keyId);
        }
        KeyUse use = key.getKeyUse();
        if (use != null && !KeyUse.SIGNATURE.equals(use)) {
            throw invalid("JWK use must be sig, got " + use);
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException(message);
    }

    private static IllegalStateException invalid(String message, Throwable cause) {
        return new IllegalStateException(message, cause);
    }

    public record LoadedJwks(JWKSet jwkSet, Set<String> keyIds) {
    }
}
