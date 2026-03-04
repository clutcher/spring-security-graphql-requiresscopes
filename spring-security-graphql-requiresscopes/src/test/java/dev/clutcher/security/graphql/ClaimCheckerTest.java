package dev.clutcher.security.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimCheckerTest {

    private final ClaimChecker featureChecker = new ClaimChecker("enabledFeatures");
    private final ClaimChecker roleChecker = new ClaimChecker("roles");

    private JwtAuthenticationToken jwtWith(String claimName, List<String> values) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim(claimName, values)
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private JwtAuthenticationToken jwtWithoutClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.put("sub", "user1"))
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void shouldReturnTrueWhenValueIsPresentInClaim() {
        Authentication auth = jwtWith("enabledFeatures", List.of("CART_SECTION", "SAVED_ITEMS_SECTION"));

        assertTrue(featureChecker.has(auth, "CART_SECTION"));
    }

    @Test
    void shouldReturnFalseWhenValueIsNotInClaim() {
        Authentication auth = jwtWith("enabledFeatures", List.of("SAVED_ITEMS_SECTION"));

        assertFalse(featureChecker.has(auth, "CART_SECTION"));
    }

    @Test
    void shouldReturnFalseWhenClaimIsAbsent() {
        Authentication auth = jwtWithoutClaim();

        assertFalse(featureChecker.has(auth, "CART_SECTION"));
    }

    @Test
    void shouldReturnFalseForNonJwtAuthentication() {
        Authentication auth = new TestingAuthenticationToken("user1", null);

        assertFalse(featureChecker.has(auth, "CART_SECTION"));
    }

    @Test
    void shouldBeCaseInsensitive() {
        Authentication auth = jwtWith("roles", List.of("specialpricinggroup"));

        assertTrue(roleChecker.has(auth, "SPECIALPRICINGGROUP"));
    }

    @Test
    void shouldReturnTrueWhenRoleIsPresentInJwtClaim() {
        Authentication auth = jwtWith("roles", List.of("specialpricinggroup", "productsgroup"));

        assertTrue(roleChecker.has(auth, "PRODUCTSGROUP"));
    }

    @Test
    void shouldReturnFalseWhenRoleIsNotInJwtClaim() {
        Authentication auth = jwtWith("roles", List.of("specialpricinggroup"));

        assertFalse(roleChecker.has(auth, "PRODUCTSGROUP"));
    }

    @Test
    void shouldReturnFalseForNullAuthentication() {
        assertFalse(roleChecker.has(null, "SPECIALPRICINGGROUP"));
    }
}