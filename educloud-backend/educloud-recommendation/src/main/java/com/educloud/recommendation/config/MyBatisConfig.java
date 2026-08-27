package com.educloud.recommendation.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.educloud.recommendation.mapper")
public class MyBatisConfig {
}
