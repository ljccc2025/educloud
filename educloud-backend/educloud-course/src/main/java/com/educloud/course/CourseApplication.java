package com.educloud.course;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * EduCloud Course 服务入口。
 *
 * <p>M05 设计规格（docs/superpowers/specs/2026-08-23-educloud-course-design.md）：
 * Course 是课程分类、课程根与不可变版本、审核状态机、生命周期、免费选课与评价的权威服务；
 * 对外 API 经 Gateway（Bearer + course:* 权限码），内部 API 用服务令牌（aud=educloud-course）。
 * Nacos 仅用于服务发现，不依赖 Nacos 配置中心。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CourseApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourseApplication.class, args);
    }
}
