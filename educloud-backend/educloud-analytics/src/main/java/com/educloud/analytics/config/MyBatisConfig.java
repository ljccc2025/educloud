package com.educloud.analytics.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.educloud.analytics.mapper")
public class MyBatisConfig {
}
