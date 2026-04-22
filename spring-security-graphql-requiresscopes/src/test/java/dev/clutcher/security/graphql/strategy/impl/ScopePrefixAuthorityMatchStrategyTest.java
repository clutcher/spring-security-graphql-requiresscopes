package dev.clutcher.security.graphql.strategy.impl;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopePrefixAuthorityMatchStrategyTest {

    private final ScopePrefixAuthorityMatchStrategy strategy = new ScopePrefixAuthorityMatchStrategy("SCOPE_");

    private Authentication authWith(String... authorities) {
        return new TestingAuthenticationToken("user", null, authorities);
    }

    @Test
    void shouldMatchApolloRawScopeAgainstPrefixedAuthority() {
        // Given
        Authentication auth = authWith("SCOPE_admin");

        // When
        boolean allowed = strategy.check(auth, "admin");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldNotMatchWhenPrefixedAuthorityIsMissing() {
        // Given
        Authentication auth = authWith("SCOPE_read");

        // When
        boolean allowed = strategy.check(auth, "admin");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldNotMatchVerbatimRawScopeWithoutPrefix() {
        // Given
        Authentication auth = authWith("admin");

        // When
        boolean allowed = strategy.check(auth, "admin");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldReturnFalseForNullAuthentication() {
        // Given
        Authentication auth = null;

        // When
        boolean allowed = strategy.check(auth, "admin");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldSupportCustomAuthorityPrefix() {
        // Given
        ScopePrefixAuthorityMatchStrategy custom = new ScopePrefixAuthorityMatchStrategy("ACCESS_");
        Authentication auth = authWith("ACCESS_editor");

        // When
        boolean allowed = custom.check(auth, "editor");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldBehaveAsVerbatimMatchWhenPrefixIsEmpty() {
        // Given
        ScopePrefixAuthorityMatchStrategy empty = new ScopePrefixAuthorityMatchStrategy("");
        Authentication auth = authWith("admin");

        // When
        boolean allowed = empty.check(auth, "admin");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldReturnFalseWhenAuthoritiesAreEmpty() {
        // Given
        Authentication auth = new TestingAuthenticationToken("user", null, List.of());

        // When
        boolean allowed = strategy.check(auth, "admin");

        // Then
        assertFalse(allowed);
    }
}
