package com.educloud.search.security;

import com.educloud.search.config.SearchProperties;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Search 服务 JWKS 密钥加载器
 * 支持从 classpath: 或 file: 路径加载公钥 JWKS，缺失或加载异常时优雅生成内存临时 RSA 密钥作为兼容 Fallback。
 */
@Slf4j
public final class JwksLoader {

    public record LoadedJwks(JWKSet jwkSet, Set<String> keyIds) {}

    public LoadedJwks load(SearchProperties properties) {
        String location = properties != null && properties.getJwt() != null ? properties.getJwt().getJwksLocation() : null;
        if (StringUtils.hasText(location)) {
            try {
                String json = readResource(location);
                JWKSet parsed = JWKSet.parse(json);
                Set<String> keyIds = new HashSet<>();
                List<JWK> publicKeys = new ArrayList<>();
                for (JWK key : parsed.getKeys()) {
                    if (key.getKeyID() != null) {
                        keyIds.add(key.getKeyID());
                    }
                    publicKeys.add(key.toPublicJWK());
                }
                if (!publicKeys.isEmpty()) {
                    return new LoadedJwks(new JWKSet(publicKeys), keyIds);
                }
            } catch (Exception e) {
                log.warn("Failed to load JWKS from {}: {}. Generating dev fallback key", location, e.getMessage());
            }
        }

        // Dev sandbox fallback: generate local ephemeral RSA key
        try {
            RSAKey rsaKey = new RSAKeyGenerator(2048)
                    .keyID("dev-fallback-key")
                    .keyUse(KeyUse.SIGNATURE)
                    .generate();
            return new LoadedJwks(new JWKSet(rsaKey.toPublicJWK()), Set.of("dev-fallback-key"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate fallback JWK", e);
        }
    }

    private String readResource(String location) throws Exception {
        Resource resource;
        if (location.startsWith("classpath:")) {
            resource = new ClassPathResource(location.substring("classpath:".length()));
        } else if (location.startsWith("file:")) {
            resource = new FileSystemResource(location.substring("file:".length()));
        } else {
            resource = new FileSystemResource(location);
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
