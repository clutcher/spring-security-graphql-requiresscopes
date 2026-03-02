package com.abb.flow.schemasecurity.authorizer;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultRoleAuthorizerTest {

    private final DefaultRoleAuthorizer authorizer = new DefaultRoleAuthorizer("ROLE_");

    private Authentication authWith(String... authorities) {
        return new TestingAuthenticationToken("user1", null,
                java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toList());
    }

    @Test
    void shouldReturnTrueWhenAuthorityMatchesWithPrefix() {
        Authentication auth = authWith("ROLE_admin");

        assertTrue(authorizer.hasRole(auth, "admin"));
    }

    @Test
    void shouldBeCaseInsensitive() {
        Authentication auth = authWith("ROLE_ADMIN");

        assertTrue(authorizer.hasRole(auth, "admin"));
    }

    @Test
    void shouldReturnFalseWhenAuthorityDoesNotMatch() {
        Authentication auth = authWith("ROLE_user");

        assertFalse(authorizer.hasRole(auth, "admin"));
    }

    @Test
    void shouldReturnFalseForNullAuthentication() {
        assertFalse(authorizer.hasRole(null, "admin"));
    }

    @Test
    void shouldReturnFalseWhenNoAuthoritiesPresent() {
        Authentication auth = new TestingAuthenticationToken("user1", null, List.of());

        assertFalse(authorizer.hasRole(auth, "admin"));
    }

    @Test
    void shouldMatchRoleInputCaseInsensitively() {
        Authentication auth = authWith("ROLE_specialpricinggroup");

        assertTrue(authorizer.hasRole(auth, "SPECIALPRICINGGROUP"));
    }

}