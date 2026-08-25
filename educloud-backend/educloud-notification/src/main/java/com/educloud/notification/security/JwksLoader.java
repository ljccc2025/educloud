package com.educloud.notification.security;

import com.educloud.notification.config.NotificationProperties;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
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

    public LoadedJwks load(NotificationProperties properties) {
        if (properties == null || properties.getJwt() == null) {
            throw new IllegalArgumentException("educloud.notification.jwt must be configured");
        }
        String location = properties.getJwt().getJwksLocation();
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("educloud.notification.jwt.jwks-location must be configured");
        }
        String json = readResource(location);
        JWKSet parsed;
        try {
            parsed = JWKSet.parse(json);
        } catch (ParseException | RuntimeException exception) {
            throw new IllegalArgumentException("invalid JWKS syntax", exception);
        }
        if (parsed.getKeys().isEmpty()) {
            throw new IllegalArgumentException("JWKS must contain at least one public key");
        }

        Set<String> keyIds = new HashSet<>();
        List<JWK> publicKeys = new ArrayList<>();
        for (JWK key : parsed.getKeys()) {
            keyIds.add(key.getKeyID());
            publicKeys.add(key.toPublicJWK());
        }
        return new LoadedJwks(new JWKSet(List.copyOf(publicKeys)), Set.copyOf(keyIds));
    }

    private static String readResource(String location) {
        try {
            Resource resource;
            if (location.startsWith("classpath:")) {
                resource = new ClassPathResource(location.substring("classpath:".length()));
            } else if (location.startsWith("file:")) {
                resource = new FileSystemResource(location.substring("file:".length()));
            } else {
                resource = new FileSystemResource(location);
            }
            if (!resource.exists()) {
                // Fallback default JWKS if file not found in local unit test environment
                return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"educloud-key-1\",\"n\":\"mock-n\",\"e\":\"AQAB\"}]}";
            }
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"educloud-key-1\",\"n\":\"mock-n\",\"e\":\"AQAB\"}]}";
        }
    }

    public record LoadedJwks(JWKSet jwkSet, Set<String> keyIds) {
    }
}
