package com.educloud.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * EduCloud User 服务入口。
 *
 * <p>M03 设计规格（docs/superpowers/specs/2026-08-20-educloud-user-design.md）：
 * User 是身份、账号、档案、RBAC、会话与平台公开配置的权威服务；只消费并验证身份凭证，
 * 不拥有课程/订单等业务事实。Nacos 仅用于服务发现，不依赖 Nacos 配置中心。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
