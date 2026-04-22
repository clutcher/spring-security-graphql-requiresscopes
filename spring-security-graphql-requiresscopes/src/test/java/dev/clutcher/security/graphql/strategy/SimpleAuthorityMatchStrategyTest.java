package dev.clutcher.security.graphql.strategy;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleAuthorityMatchStrategyTest {

    private final SimpleAuthorityMatchStrategy strategy = new SimpleAuthorityMatchStrategy();

    private Authentication authWith(String... authorities) {
        return new TestingAuthenticationToken("user", null, authorities);
    }

    @Test
    void shouldReturnTrueWhenScopeMatchesAuthorityExactly() {
        // Given
        Authentication auth = authWith("feature:PRICING");

        // When
        boolean allowed = strategy.check(auth, "feature:PRICING");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldReturnTrueWhenScopeInputIsLowercaseAndAuthorityIsUppercase() {
        // Given
        Authentication auth = authWith("FEATURE:PRICING");

        // When
        boolean allowed = strategy.check(auth, "feature:pricing");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldReturnFalseWhenAuthorityIsLowercaseAndScopeIsUppercase() {
        // Given
        Authentication auth = authWith("feature:pricing");

        // When
        boolean allowed = strategy.check(auth, "feature:PRICING");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldReturnFalseWhenScopeNotInAuthorities() {
        // Given
        Authentication auth = authWith("feature:CART");

        // When
        boolean allowed = strategy.check(auth, "feature:PRICING");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldReturnFalseWhenAuthoritiesAreEmpty() {
        // Given
        Authentication auth = new TestingAuthenticationToken("user", null, List.of());

        // When
        boolean allowed = strategy.check(auth, "feature:PRICING");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldReturnFalseForNullAuthentication() {
        // Given
        Authentication auth = null;

        // When
        boolean allowed = strategy.check(auth, "feature:PRICING");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldReturnTrueWhenOneOfMultipleAuthoritiesMatches() {
        // Given
        Authentication auth = authWith("feature:CART", "feature:PRICING", "role:ADMIN");

        // When
        boolean allowed = strategy.check(auth, "feature:PRICING");

        // Then
        assertTrue(allowed);
    }
}
