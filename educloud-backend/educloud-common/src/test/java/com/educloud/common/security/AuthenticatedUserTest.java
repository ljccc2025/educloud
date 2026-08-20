package com.educloud.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthenticatedUserTest {

    @Test
    void defensivelyCopiesRolesAndPermissions() {
        var roles = new HashSet<>(Set.of("TEACHER"));
        var permissions = new HashSet<>(Set.of("course:write"));

        var user = new AuthenticatedUser("user-1", "session-1", roles, permissions);
        roles.add("ADMIN");
        permissions.add("system:write");

        assertThat(user.roles()).containsExactly("TEACHER");
        assertThat(user.permissions()).containsExactly("course:write");
        assertThatThrownBy(() -> user.roles().add("ADMIN"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingIdentityFieldsAndCollections() {
        assertThatThrownBy(() -> new AuthenticatedUser(" ", "session-1", Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticatedUser("user-1", " ", Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticatedUser("user-1", "session-1", null, Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuthenticatedUser("user-1", "session-1", Set.of(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
