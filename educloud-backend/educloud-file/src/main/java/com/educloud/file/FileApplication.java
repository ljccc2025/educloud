package com.educloud.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * EduCloud File 服务入口。
 *
 * <p>M04 设计规格（docs/superpowers/specs/2026-08-22-educloud-file-design.md）：
 * File 是文件生命周期、MinIO 对象映射与受控业务绑定的权威服务；对外 API 经 Gateway
 * （Bearer + 权限码），内部 API 用服务令牌（aud=educloud-file + clientId 白名单）。
 * Nacos 仅用于服务发现，不依赖 Nacos 配置中心。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FileApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileApplication.class, args);
    }
}
