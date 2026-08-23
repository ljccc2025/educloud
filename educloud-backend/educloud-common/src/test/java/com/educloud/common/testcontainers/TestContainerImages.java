package com.educloud.common.testcontainers;

import java.util.function.Function;
import org.testcontainers.utility.DockerImageName;

public final class TestContainerImages {

    public static final String REDIS_IMAGE_ENV = "EDUCLOUD_TEST_REDIS_IMAGE";
    private static final String DEFAULT_REDIS_IMAGE = "redis:7.2.5-alpine";

    private TestContainerImages() {}

    public static DockerImageName redis() {
        return redis(System::getenv);
    }

    static DockerImageName redis(Function<String, String> environment) {
        return resolve(REDIS_IMAGE_ENV, DEFAULT_REDIS_IMAGE, environment)
                .asCompatibleSubstituteFor("redis");
    }

    private static DockerImageName resolve(
            String variable, String defaultImage, Function<String, String> environment) {
        String override = environment.apply(variable);
        String candidate = override == null ? defaultImage : override;
        if (override != null && (override.isBlank() || !override.equals(override.trim()))) {
            throw invalid(
                    variable,
                    "must be a non-blank image reference without surrounding whitespace",
                    null);
        }
        if (candidate.indexOf('@') >= 0) {
            throw invalid(variable, "must use an explicit tag instead of a digest", null);
        }
        try {
            DockerImageName image = DockerImageName.parse(candidate);
            image.assertValid();
            if ("latest".equalsIgnoreCase(image.getVersionPart())) {
                throw invalid(variable, "must use an explicit non-latest tag", null);
            }
            return image;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith(variable + " ")) {
                throw exception;
            }
            throw invalid(variable, "contains an invalid Docker image reference", exception);
        }
    }

    private static IllegalArgumentException invalid(
            String variable, String detail, Exception cause) {
        return new IllegalArgumentException(variable + " " + detail, cause);
    }
}
