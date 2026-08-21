package com.educloud.user.security;

import com.educloud.user.config.PasswordProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码哈希配置。依据：M03 设计规格第 5 节（BCrypt strength 10，Spring Security 自适应哈希）。
 */
@Configuration(proxyBeanMethods = false)
public class PasswordConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder(PasswordProperties properties) {
        return new BCryptPasswordEncoder(properties.bcryptStrength());
    }
}
