package com.educloud.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

    @Test
    void identifiesTheSameRequestAndConflictingPayloads() {
        var original = new IdempotencyKey("user-1", "order:create", "key-1", "sha256:a");
        var same = new IdempotencyKey("user-1", "order:create", "key-1", "sha256:a");
        var conflicting = new IdempotencyKey("user-1", "order:create", "key-1", "sha256:b");

        assertThat(original.sameScope(same)).isTrue();
        assertThat(original.representsSameRequest(same)).isTrue();
        assertThat(original.conflictsWith(same)).isFalse();
        assertThat(original.sameScope(conflicting)).isTrue();
        assertThat(original.representsSameRequest(conflicting)).isFalse();
        assertThat(original.conflictsWith(conflicting)).isTrue();
    }

    @Test
    void doesNotConflictAcrossScopes() {
        var original = new IdempotencyKey("user-1", "order:create", "key-1", "sha256:a");

        assertThat(original.conflictsWith(
                new IdempotencyKey("user-2", "order:create", "key-1", "sha256:b"))).isFalse();
        assertThat(original.conflictsWith(
                new IdempotencyKey("user-1", "order:cancel", "key-1", "sha256:b"))).isFalse();
        assertThat(original.conflictsWith(
                new IdempotencyKey("user-1", "order:create", "key-2", "sha256:b"))).isFalse();
    }

    @Test
    void trimsAndRejectsBlankFields() {
        var key = new IdempotencyKey(" user-1 ", " order:create ", " key-1 ", " sha256:a ");

        assertThat(key).isEqualTo(
                new IdempotencyKey("user-1", "order:create", "key-1", "sha256:a"));
        assertThatThrownBy(() -> new IdempotencyKey(" ", "operation", "key", "digest"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey("actor", " ", "key", "digest"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey("actor", "operation", " ", "digest"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey("actor", "operation", "key", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
