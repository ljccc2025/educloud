package com.educloud.file.controller;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 测试注解：向 SecurityContext 注入带 permissions claim 的 JwtAuthenticationToken。
 *
 * <p>权限码（如 file:upload）无前缀映射为 authority，与
 * {@code SecurityConfiguration.jwtAuthenticationConverter} 的线上语义一致；
 * sub 默认 1001，可覆盖。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithMockJwtSecurityContextFactory.class)
public @interface WithMockJwt {

    String subject() default "1001";

    String[] permissions() default {};
}
