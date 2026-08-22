package com.educloud.file.security;

import com.educloud.file.config.FileProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
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

/**
 * 静态加载 User 公钥 JWKS（复制 gateway JwksLoader 适配 File）。
 *
 * <p>来源唯一：{@code educloud.file.jwt.jwks-location}（本地文件，支持 file: 前缀）。
 * 只接受 RSA 公钥（use=sig、alg=RS256、非空 kid、无重复 kid、无私钥参数），
 * 任一违规直接启动失败（fail-fast）。</p>
 */
public final class JwksLoader {

    private static final int MAX_JWKS_BYTES = 256 * 1024;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> PRIVATE_RSA_PARAMETERS = Set.of(
            "d", "p", "q", "dp", "dq", "qi", "oth");

    public LoadedJwks load(FileProperties properties) {
        String json = readFile(properties);
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

    private static String readFile(FileProperties properties) {
        String location = properties.jwt().jwksLocation();
        if (!StringUtils.hasText(location)) {
            throw invalid("educloud.file.jwt.jwks-location must be configured");
        }
        String pathValue = location.startsWith("file:") ? location.substring("file:".length()) : location;
        Path path = Path.of(pathValue);
        try {
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw invalid("educloud.file.jwt.jwks-location must be a regular readable file");
            }
            if (Files.size(path) > MAX_JWKS_BYTES) {
                throw invalid("educloud.file.jwt.jwks-location exceeds 256 KiB");
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw invalid("educloud.file.jwt.jwks-location cannot be read", exception);
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
