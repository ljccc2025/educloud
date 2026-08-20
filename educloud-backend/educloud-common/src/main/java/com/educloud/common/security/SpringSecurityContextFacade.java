package com.educloud.common.security;

import java.util.Objects;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

public final class SpringSecurityContextFacade implements SecurityContextFacade {

    private final SecurityContextHolderStrategy contextHolderStrategy;

    public SpringSecurityContextFacade(SecurityContextHolderStrategy contextHolderStrategy) {
        this.contextHolderStrategy = Objects.requireNonNull(contextHolderStrategy, "contextHolderStrategy");
    }

    @Override
    public Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = contextHolderStrategy.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return Optional.of(authenticatedUser);
        }
        return Optional.empty();
    }
}
