package dev.clutcher.security.graphql.strategy;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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
        Authentication auth = authWith("feature:PRICING");

        assertTrue(strategy.check(auth, "feature:PRICING"));
    }

    @Test
    void shouldReturnTrueForCaseInsensitiveMatch() {
        Authentication auth = authWith("feature:pricing");

        assertTrue(strategy.check(auth, "feature:PRICING"));
    }

    @Test
    void shouldReturnFalseWhenScopeNotInAuthorities() {
        Authentication auth = authWith("feature:CART");

        assertFalse(strategy.check(auth, "feature:PRICING"));
    }

    @Test
    void shouldReturnFalseWhenAuthoritiesAreEmpty() {
        Authentication auth = new TestingAuthenticationToken("user", null, List.of());

        assertFalse(strategy.check(auth, "feature:PRICING"));
    }

    @Test
    void shouldReturnFalseForNullAuthentication() {
        assertFalse(strategy.check(null, "feature:PRICING"));
    }

    @Test
    void shouldReturnTrueWhenOneOfMultipleAuthoritiesMatches() {
        Authentication auth = authWith("feature:CART", "feature:PRICING", "role:ADMIN");

        assertTrue(strategy.check(auth, "feature:PRICING"));
    }
}
