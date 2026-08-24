package com.educloud.content.testcontainers;

import org.testcontainers.utility.DockerImageName;

import java.util.function.Function;

public final class TestContainerImages {

    static final String MYSQL_IMAGE_ENV = "EDUCLOUD_TEST_MYSQL_IMAGE";
    private static final String DEFAULT_MYSQL_IMAGE = "mysql:8.0.36";

    static final String RABBITMQ_IMAGE_ENV = "EDUCLOUD_TEST_RABBITMQ_IMAGE";
    private static final String DEFAULT_RABBITMQ_IMAGE = "rabbitmq:3.13-management-alpine";

    static final String REDIS_IMAGE_ENV = "EDUCLOUD_TEST_REDIS_IMAGE";
    private static final String DEFAULT_REDIS_IMAGE = "redis:7.2.5-alpine";

    private TestContainerImages() {
    }

    public static DockerImageName mysql() {
        return mysql(System::getenv);
    }

    static DockerImageName mysql(Function<String, String> environment) {
        return resolve(MYSQL_IMAGE_ENV, DEFAULT_MYSQL_IMAGE, environment)
                .asCompatibleSubstituteFor("mysql");
    }

    public static DockerImageName rabbitmq() {
        return rabbitmq(System::getenv);
    }

    static DockerImageName rabbitmq(Function<String, String> environment) {
        return resolve(RABBITMQ_IMAGE_ENV, DEFAULT_RABBITMQ_IMAGE, environment)
                .asCompatibleSubstituteFor("rabbitmq");
    }

    public static DockerImageName redis() {
        return redis(System::getenv);
    }

    static DockerImageName redis(Function<String, String> environment) {
        return resolve(REDIS_IMAGE_ENV, DEFAULT_REDIS_IMAGE, environment)
                .asCompatibleSubstituteFor("redis");
    }

    static DockerImageName resolve(
            String variable, String defaultImage, Function<String, String> environment) {
        String override = environment.apply(variable);
        String candidate = override == null ? defaultImage : override;
        if (override != null && (override.isBlank() || !override.equals(override.trim()))) {
            throw new IllegalArgumentException(variable + " must be a non-blank image reference without surrounding whitespace");
        }
        if (candidate.indexOf('@') >= 0) {
            throw new IllegalArgumentException(variable + " must use an explicit tag instead of a digest");
        }
        DockerImageName image = DockerImageName.parse(candidate);
        image.assertValid();
        return image;
    }
}
