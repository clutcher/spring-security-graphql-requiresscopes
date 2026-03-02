package com.abb.flow.schemasecurity.authorizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultFeatureAuthorizerTest {

    private DefaultFeatureAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        authorizer = new DefaultFeatureAuthorizer();
    }

    private JwtAuthenticationToken jwtWith(List<String> features) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("enabledFeatures", features)
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private JwtAuthenticationToken jwtWithoutFeaturesClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.put("sub", "user1"))
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void shouldReturnTrueWhenFeatureIsInJwtClaim() {
        JwtAuthenticationToken auth = jwtWith(List.of("FEATURE_X", "FEATURE_Y"));

        assertTrue(authorizer.hasFeature(auth, "FEATURE_X"));
    }

    @Test
    void shouldReturnFalseWhenFeatureIsNotInJwtClaim() {
        JwtAuthenticationToken auth = jwtWith(List.of("FEATURE_Y"));

        assertFalse(authorizer.hasFeature(auth, "FEATURE_X"));
    }

    @Test
    void shouldReturnFalseWhenEnabledFeaturesClaimIsAbsent() {
        JwtAuthenticationToken auth = jwtWithoutFeaturesClaim();

        assertFalse(authorizer.hasFeature(auth, "FEATURE_X"));
    }

    @Test
    void shouldReturnFalseForNonJwtAuthentication() {
        Authentication auth = new TestingAuthenticationToken("user1", null);

        assertFalse(authorizer.hasFeature(auth, "FEATURE_X"));
    }

}