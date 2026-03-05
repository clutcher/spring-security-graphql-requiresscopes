package dev.clutcher.security.graphql.strategy;

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
        Authentication auth = authWith("FEATURE_PRICING");

        assertTrue(strategy.check(auth, "feature:PRICING"));
    }

    @Test
    void shouldTransformRoleScopeToAuthorityAndMatch() {
        Authentication auth = authWith("ROLE_ADMIN");

        assertTrue(strategy.check(auth, "role:ADMIN"));
    }

    @Test
    void shouldReturnTrueForCaseInsensitiveMatch() {
        Authentication auth = authWith("feature_pricing");

        assertTrue(strategy.check(auth, "feature:PRICING"));
    }

    @Test
    void shouldReturnFalseWhenTransformedAuthorityIsMissing() {
        Authentication auth = authWith("FEATURE_CART");

        assertFalse(strategy.check(auth, "feature:PRICING"));
    }

    @Test
    void shouldReturnFalseWhenNoMappingMatchesScopePrefix() {
        Authentication auth = authWith("SOMETHING_PRICING");

        assertFalse(strategy.check(auth, "unknown:PRICING"));
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
    void shouldRespectInsertionOrderForPrefixMatching() {
        // "feature:" is checked before "feat:" — insertion order matters
        Map<String, String> orderedMappings = new LinkedHashMap<>();
        orderedMappings.put("feature:", "FEATURE_");
        orderedMappings.put("feat:", "FEAT_");
        ClaimPrefixMappingStrategy ordered = new ClaimPrefixMappingStrategy(orderedMappings);

        Authentication auth = authWith("FEATURE_PRICING");

        assertTrue(ordered.check(auth, "feature:PRICING"));
    }

    @Test
    void shouldSupportCustomMappingsFromJwtConverterConfiguration() {
        // Simulates a mapping detected from JwtGrantedAuthoritiesConverter:
        // claim "roles" with prefix "ROLE_"
        ClaimPrefixMappingStrategy custom = new ClaimPrefixMappingStrategy(
                Map.of("roles:", "ROLE_")
        );
        Authentication auth = authWith("ROLE_EDITOR");

        assertTrue(custom.check(auth, "roles:EDITOR"));
    }
}
