package com.educloud.file.controller;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.List;

/** {@link WithMockJwt} 的实现：构造带 permissions claim 的 Jwt 并注入 SecurityContext。 */
public final class WithMockJwtSecurityContextFactory
        implements WithSecurityContextFactory<WithMockJwt> {

    @Override
    public SecurityContext createSecurityContext(WithMockJwt annotation) {
        List<String> permissions = List.of(annotation.permissions());
        Jwt jwt = Jwt.withTokenValue("with-mock-jwt")
                .header("alg", "none")
                .header("typ", "JWT")
                .subject(annotation.subject())
                .claim("permissions", permissions)
                .build();
        List<GrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .map(authority -> (GrantedAuthority) authority)
                .toList();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }
}
