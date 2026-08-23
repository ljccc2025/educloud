package com.educloud.course.testcontainers;

import java.util.function.Function;

import org.testcontainers.utility.DockerImageName;

/**
 * 测试镜像解析器：支持 `EDUCLOUD_TEST_MYSQL_IMAGE` / `EDUCLOUD_TEST_RABBITMQ_IMAGE` /
 * `EDUCLOUD_TEST_REDIS_IMAGE` 私有镜像覆盖（沿用 M01/M02 的 TestContainerImages 模式）。
 * 依据：M02 提交 ab01feb「allow private integration images」与模块契约「Testcontainers 镜像固定可覆盖来源」。
 */
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
        return resolve(MYSQL_IMAGE_ENV, DEFAULT_MYSQL_IMAGE, environment);
    }

    public static DockerImageName rabbitmq() {
        return rabbitmq(System::getenv);
    }

    static DockerImageName rabbitmq(Function<String, String> environment) {
        return resolve(RABBITMQ_IMAGE_ENV, DEFAULT_RABBITMQ_IMAGE, environment);
    }

    public static DockerImageName redis() {
        return redis(System::getenv);
    }

    static DockerImageName redis(Function<String, String> environment) {
        return resolve(REDIS_IMAGE_ENV, DEFAULT_REDIS_IMAGE, environment);
    }

    static DockerImageName resolve(
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
