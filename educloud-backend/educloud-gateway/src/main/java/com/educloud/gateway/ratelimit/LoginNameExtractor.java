package com.educloud.gateway.ratelimit;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

@Component
public final class LoginNameExtractor {

    private final ObjectMapper objectMapper;

    public LoginNameExtractor(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public String extract(byte[] body) {
        if (body == null || body.length == 0) {
            throw new LoginNameExtractionException();
        }
        try {
            JsonNode root = objectMapper.reader()
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .readTree(body);
            if (root == null || !root.isObject()) {
                throw new LoginNameExtractionException();
            }
            JsonNode loginName = root.get("loginName");
            if (loginName == null || !loginName.isTextual()) {
                throw new LoginNameExtractionException();
            }
            String normalized = Normalizer.normalize(loginName.textValue(), Normalizer.Form.NFKC)
                    .trim()
                    .toLowerCase(Locale.ROOT);
            if (normalized.isEmpty() || normalized.length() > 128) {
                throw new LoginNameExtractionException();
            }
            return normalized;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof LoginNameExtractionException extractionException) {
                throw extractionException;
            }
            throw new LoginNameExtractionException();
        }
    }

    public static final class LoginNameExtractionException extends RuntimeException {

        public LoginNameExtractionException() {
            super("loginName could not be parsed safely");
        }
    }
}
