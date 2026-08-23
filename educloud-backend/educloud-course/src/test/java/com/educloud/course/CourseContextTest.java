package com.educloud.course;

import com.educloud.course.security.TestJwtKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M05 计划任务 0：最小上下文启动测试（@SpringBootTest + test profile）。
 *
 * <p>依据：M05 实施计划任务 0 步骤 1-3（先写上下文测试，无实现时失败）。
 * test profile（application-test.yml）通过 spring.autoconfigure.exclude 排除
 * DataSource/Redis/RabbitMQ/Nacos 自动配置并关闭 Nacos 开关，保证上下文加载不连接
 * 任何外部中间件；骨架阶段除启动类外无其他业务 Bean，故无需 mock 外部依赖。</p>
 *
 * <p>任务 6 起 JwtDecoderConfiguration 启动时静态加载 User 公钥 JWKS：本测试生成临时
 * RSA 密钥对，JWKS 文件经 @DynamicPropertySource 注入 educloud.course.jwt.jwks-location
 * （与 educloud-file FileApplicationContextTest 同法），保证安全配置引入后上下文仍可
 * 无外部连接启动。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class CourseContextTest {

    private static final TestJwtKeys TEST_KEYS = new TestJwtKeys();

    @Autowired
    ConfigurableApplicationContext context;

    @DynamicPropertySource
    static void jwksLocation(DynamicPropertyRegistry registry) throws Exception {
        Path jwksFile = Files.createTempFile("course-context-jwks-", ".json");
        Files.writeString(jwksFile, TEST_KEYS.publicJwksJson());
        registry.add("educloud.course.jwt.jwks-location", () -> "file:" + jwksFile.toAbsolutePath());
    }

    @Test
    void loadsCourseContextWithoutExternalConnections() {
        assertThat(context).isNotNull();
        assertThat(context.isActive()).isTrue();
    }
}
