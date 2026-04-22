package dev.clutcher.security.graphql.strategy;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePrefixAuthorityMatchStrategyTest {

    private final RolePrefixAuthorityMatchStrategy strategy = new RolePrefixAuthorityMatchStrategy("role:", "ROLE_");

    private Authentication authWith(String... authorities) {
        return new TestingAuthenticationToken("user", null, authorities);
    }

    @Test
    void shouldReturnTrueWhenRoleAuthorityExists() {
        // Given
        Authentication auth = authWith("ROLE_ADMIN");

        // When
        boolean allowed = strategy.check(auth, "role:ADMIN");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldReturnTrueWhenScopeValueIsLowercaseAndAuthorityIsUppercase() {
        // Given
        Authentication auth = authWith("ROLE_ADMIN");

        // When
        boolean allowed = strategy.check(auth, "role:admin");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldReturnFalseWhenAuthorityIsLowercaseAndScopeValueIsUppercase() {
        // Given
        Authentication auth = authWith("role_admin");

        // When
        boolean allowed = strategy.check(auth, "role:ADMIN");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldReturnFalseWhenRoleAuthorityIsMissing() {
        // Given
        Authentication auth = authWith("ROLE_USER");

        // When
        boolean allowed = strategy.check(auth, "role:ADMIN");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldReturnFalseWhenAuthoritiesAreEmpty() {
        // Given
        Authentication auth = new TestingAuthenticationToken("user", null, List.of());

        // When
        boolean allowed = strategy.check(auth, "role:ADMIN");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldReturnFalseForNullAuthentication() {
        // Given
        Authentication auth = null;

        // When
        boolean allowed = strategy.check(auth, "role:ADMIN");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldReturnFalseWhenScopeDoesNotStartWithRolePrefix() {
        // Given
        Authentication auth = authWith("ROLE_ADMIN", "FEATURE_PRICING");

        // When
        boolean allowed = strategy.check(auth, "feature:PRICING");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldSupportCustomScopeAndAuthorityPrefixes() {
        // Given
        RolePrefixAuthorityMatchStrategy custom =
                new RolePrefixAuthorityMatchStrategy("grp:", "GROUP_");
        Authentication auth = authWith("GROUP_EDITORS");

        // When
        boolean allowed = custom.check(auth, "grp:EDITORS");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldUseConfiguredRolePrefixFromGrantedAuthorityDefaults() {
        // Given
        RolePrefixAuthorityMatchStrategy custom =
                new RolePrefixAuthorityMatchStrategy("role:", "CUSTOM_ROLE_");
        Authentication auth = authWith("CUSTOM_ROLE_ADMIN");

        // When
        boolean allowed = custom.check(auth, "role:ADMIN");

        // Then
        assertTrue(allowed);
    }
}
