package dev.clutcher.security.graphql.strategy;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePrefixAuthorityMatchStrategyTest {

    private final RolePrefixAuthorityMatchStrategy strategy = new RolePrefixAuthorityMatchStrategy();

    private Authentication authWith(String... authorities) {
        return new TestingAuthenticationToken("user", null, authorities);
    }

    @Test
    void shouldReturnTrueWhenRoleAuthorityExists() {
        Authentication auth = authWith("ROLE_ADMIN");

        assertTrue(strategy.check(auth, "role:ADMIN"));
    }

    @Test
    void shouldReturnTrueForCaseInsensitiveMatch() {
        Authentication auth = authWith("role_admin");

        assertTrue(strategy.check(auth, "role:ADMIN"));
    }

    @Test
    void shouldReturnFalseWhenRoleAuthorityIsMissing() {
        Authentication auth = authWith("ROLE_USER");

        assertFalse(strategy.check(auth, "role:ADMIN"));
    }

    @Test
    void shouldReturnFalseWhenAuthoritiesAreEmpty() {
        Authentication auth = new TestingAuthenticationToken("user", null, List.of());

        assertFalse(strategy.check(auth, "role:ADMIN"));
    }

    @Test
    void shouldReturnFalseForNullAuthentication() {
        assertFalse(strategy.check(null, "role:ADMIN"));
    }

    @Test
    void shouldReturnFalseWhenScopeDoesNotStartWithRolePrefix() {
        Authentication auth = authWith("ROLE_ADMIN", "FEATURE_PRICING");

        assertFalse(strategy.check(auth, "feature:PRICING"));
    }

    @Test
    void shouldSupportCustomScopeAndAuthorityPrefixes() {
        RolePrefixAuthorityMatchStrategy custom =
                new RolePrefixAuthorityMatchStrategy("grp:", "GROUP_");
        Authentication auth = authWith("GROUP_EDITORS");

        assertTrue(custom.check(auth, "grp:EDITORS"));
    }

    @Test
    void shouldUseConfiguredRolePrefixFromGrantedAuthorityDefaults() {
        RolePrefixAuthorityMatchStrategy custom =
                new RolePrefixAuthorityMatchStrategy("role:", "CUSTOM_ROLE_");
        Authentication auth = authWith("CUSTOM_ROLE_ADMIN");

        assertTrue(custom.check(auth, "role:ADMIN"));
    }
}
