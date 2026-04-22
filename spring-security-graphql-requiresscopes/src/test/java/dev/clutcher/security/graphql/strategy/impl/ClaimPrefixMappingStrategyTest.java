package dev.clutcher.security.graphql.strategy.impl;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimPrefixMappingStrategyTest {

    private final ClaimPrefixMappingStrategy strategy = new ClaimPrefixMappingStrategy(
            Map.of("feature:", "FEATURE_", "role:", "ROLE_")
    );

    private Authentication authWith(String... authorities) {
        return new TestingAuthenticationToken("user", null, authorities);
    }

    @Test
    void shouldTransformFeatureScopeToAuthorityAndMatch() {
        // Given
        Authentication auth = authWith("FEATURE_PRICING");

        // When
        boolean allowed = strategy.check(auth, "feature:PRICING");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldTransformRoleScopeToAuthorityAndMatch() {
        // Given
        Authentication auth = authWith("ROLE_ADMIN");

        // When
        boolean allowed = strategy.check(auth, "role:ADMIN");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldReturnFalseWhenTransformedAuthorityCaseDiffers() {
        // Given
        Authentication auth = authWith("feature_pricing");

        // When
        boolean allowed = strategy.check(auth, "feature:PRICING");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldReturnFalseWhenTransformedAuthorityIsMissing() {
        // Given
        Authentication auth = authWith("FEATURE_CART");

        // When
        boolean allowed = strategy.check(auth, "feature:PRICING");

        // Then
        assertFalse(allowed);
    }

    @Test
    void shouldReturnFalseWhenNoMappingMatchesScopePrefix() {
        // Given
        Authentication auth = authWith("SOMETHING_PRICING");

        // When
        boolean allowed = strategy.check(auth, "unknown:PRICING");

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
    void shouldRespectInsertionOrderForPrefixMatching() {
        // Given
        Map<String, String> orderedMappings = new LinkedHashMap<>();
        orderedMappings.put("feature:", "FEATURE_");
        orderedMappings.put("feat:", "FEAT_");
        ClaimPrefixMappingStrategy ordered = new ClaimPrefixMappingStrategy(orderedMappings);
        Authentication auth = authWith("FEATURE_PRICING");

        // When
        boolean allowed = ordered.check(auth, "feature:PRICING");

        // Then
        assertTrue(allowed);
    }

    @Test
    void shouldSupportCustomMappingsFromJwtConverterConfiguration() {
        // Given
        ClaimPrefixMappingStrategy custom = new ClaimPrefixMappingStrategy(
                Map.of("roles:", "ROLE_")
        );
        Authentication auth = authWith("ROLE_EDITOR");

        // When
        boolean allowed = custom.check(auth, "roles:EDITOR");

        // Then
        assertTrue(allowed);
    }
}
