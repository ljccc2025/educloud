package com.educloud.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SpringSecurityContextFacadeTest {

    private final SpringSecurityContextFacade facade = new SpringSecurityContextFacade(
            SecurityContextHolder.getContextHolderStrategy());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsAnAuthenticatedCommonPrincipal() {
        var user = new AuthenticatedUser(
                "user-1", "session-1", Set.of("STUDENT"), Set.of("course:read"));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                user,
                "ignored-credential",
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(facade.currentUser()).contains(user);
    }

    @Test
    void returnsEmptyForAnonymousUnauthenticatedAndForeignPrincipals() {
        assertThat(facade.currentUser()).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser("user-1", "session-1", Set.of(), Set.of()),
                        "credential"));
        assertThat(facade.currentUser()).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "anonymous-key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThat(facade.currentUser()).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("forged-user", "credential", List.of()));
        assertThat(facade.currentUser()).isEmpty();
    }
}
