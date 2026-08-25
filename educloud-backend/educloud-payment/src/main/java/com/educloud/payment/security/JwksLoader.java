package com.educloud.payment.security;

import com.educloud.payment.config.PaymentProperties;
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

    public LoadedJwks load(PaymentProperties properties) {
        if (properties == null || properties.jwt() == null) {
            throw invalid("educloud.payment.jwt must be configured");
        }
        String location = properties.jwt().jwksLocation();
        if (!StringUtils.hasText(location)) {
            throw invalid("educloud.payment.jwt.jwks-location must be configured");
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

    private static void validateKey(JWK key, Set<String> keyIds) {
        if (!KeyType.RSA.equals(key.getKeyType())) {
            throw invalid("unsupported key type: " + key.getKeyType());
        }
        if (!(key instanceof RSAKey rsaKey)) {
            throw invalid("key is not an RSA key: " + key.getKeyID());
        }
        if (rsaKey.isPrivate()) {
            throw invalid("JWKS must not contain private key parameters");
        }
        if (key.getKeyUse() != null && !KeyUse.SIGNATURE.equals(key.getKeyUse())) {
            throw invalid("unsupported key use: " + key.getKeyUse());
        }
        if (key.getAlgorithm() != null && !"RS256".equals(key.getAlgorithm().getName())) {
            throw invalid("unsupported algorithm: " + key.getAlgorithm());
        }
        if (!StringUtils.hasText(key.getKeyID())) {
            throw invalid("JWKS key must declare a non-empty kid");
        }
        if (!keyIds.add(key.getKeyID())) {
            throw invalid("duplicate kid in JWKS: " + key.getKeyID());
        }
    }

    private static void rejectPrivateParameters(String json) {
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            throw invalid("invalid JSON syntax in JWKS", exception);
        }
        JsonNode keys = root.get("keys");
        if (keys == null || !keys.isArray()) {
            throw invalid("JWKS JSON must contain a 'keys' array");
        }
        for (JsonNode key : keys) {
            for (String param : PRIVATE_RSA_PARAMETERS) {
                if (key.hasNonNull(param)) {
                    throw invalid("JWKS must not contain private parameter: " + param);
                }
            }
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
        if (!resource.exists()) {
            throw invalid("JWKS resource does not exist: " + location);
        }
        try {
            long length = resource.contentLength();
            if (length > MAX_JWKS_BYTES) {
                throw invalid("JWKS resource exceeds size limit: " + length + " bytes");
            }
            byte[] bytes = resource.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw invalid("failed to read JWKS resource: " + location, exception);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("JWKS error: " + message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException("JWKS error: " + message, cause);
    }

    public record LoadedJwks(JWKSet jwkSet, Set<String> keyIds) {
    }
}
