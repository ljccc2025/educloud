package com.educloud.search.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * 内部微服务通信认证令牌
 */
public class InternalApiAuthenticationToken extends AbstractAuthenticationToken {

    private final String clientId;

    public InternalApiAuthenticationToken(String clientId, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.clientId = clientId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return clientId;
    }

    public String getClientId() {
        return clientId;
    }
}
