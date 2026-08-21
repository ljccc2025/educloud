package com.educloud.gateway.security;

import com.educloud.gateway.config.GatewaySecurityProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public LoadedJwks load(GatewaySecurityProperties properties) {
        String json = readExactlyOneSource(properties);
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

    private static String readExactlyOneSource(GatewaySecurityProperties properties) {
        boolean hasJson = StringUtils.hasText(properties.getJwksJson());
        boolean hasLocation = properties.getJwksLocation() != null;
        if (hasJson == hasLocation) {
            throw invalid("exactly one JWKS source must be configured");
        }
        if (hasJson) {
            byte[] bytes = properties.getJwksJson().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_JWKS_BYTES) {
                throw invalid("educloud.gateway.security.jwks-json exceeds 256 KiB");
            }
            return properties.getJwksJson();
        }
        return readFile(properties.getJwksLocation());
    }

    private static String readFile(Resource resource) {
        try {
            if (!resource.isFile()) {
                throw invalid("educloud.gateway.security.jwks-location must be a regular readable file");
            }
            Path path = resource.getFile().toPath();
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw invalid("educloud.gateway.security.jwks-location must be a regular readable file");
            }
            if (Files.size(path) > MAX_JWKS_BYTES) {
                throw invalid("educloud.gateway.security.jwks-location exceeds 256 KiB");
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw invalid("educloud.gateway.security.jwks-location cannot be read", exception);
        }
    }

    private static void validateKey(JWK key, Set<String> keyIds) {
        if (!KeyType.RSA.equals(key.getKeyType()) || !(key instanceof RSAKey rsaKey)) {
            throw invalid("every JWKS key must be an RSA public key");
        }
        if (key.toJSONObject().keySet().stream().anyMatch(PRIVATE_RSA_PARAMETERS::contains)
                || key.isPrivate()) {
            throw invalid("JWKS must not contain private key parameters");
        }
        if (!KeyUse.SIGNATURE.equals(key.getKeyUse())) {
            throw invalid("every JWKS key must declare use=sig");
        }
        if (!JWSAlgorithm.RS256.equals(key.getAlgorithm())) {
            throw invalid("every JWKS key must declare alg=RS256");
        }
        if (!StringUtils.hasText(key.getKeyID())) {
            throw invalid("every JWKS key must declare a non-blank kid");
        }
        if (!keyIds.add(key.getKeyID())) {
            throw invalid("JWKS contains a duplicate kid");
        }
        try {
            rsaKey.toRSAPublicKey();
        } catch (JOSEException | RuntimeException exception) {
            throw invalid("every JWKS key must contain a usable RSA public key", exception);
        }
    }

    private static IllegalStateException invalid(String category) {
        return new IllegalStateException("invalid JWKS configuration: " + category);
    }

    private static IllegalStateException invalid(String category, Exception cause) {
        return new IllegalStateException("invalid JWKS configuration: " + category, cause);
    }

    public record LoadedJwks(JWKSet jwkSet, Set<String> keyIds) {
    }
}
