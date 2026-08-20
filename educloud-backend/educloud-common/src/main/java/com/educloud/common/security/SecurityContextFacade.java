package com.educloud.common.security;

import java.util.Optional;

public interface SecurityContextFacade {

    Optional<AuthenticatedUser> currentUser();
}
