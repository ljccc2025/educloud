package com.educloud.course;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M05 计划任务 0：最小上下文启动测试（@SpringBootTest + test profile）。
 *
 * <p>依据：M05 实施计划任务 0 步骤 1-3（先写上下文测试，无实现时失败）。
 * test profile（application-test.yml）通过 spring.autoconfigure.exclude 排除
 * DataSource/Redis/RabbitMQ/Nacos 自动配置并关闭 Nacos 开关，保证上下文加载不连接
 * 任何外部中间件；骨架阶段除启动类外无其他业务 Bean，故无需 mock 外部依赖。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class CourseContextTest {

    @Autowired
    ConfigurableApplicationContext context;

    @Test
    void loadsCourseContextWithoutExternalConnections() {
        assertThat(context).isNotNull();
        assertThat(context.isActive()).isTrue();
    }
}
