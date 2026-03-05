package dev.clutcher.security.graphql.strategy;

import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Objects;

/**
 * Strategy 3: maps scope-type prefixes to Spring Security authority prefixes using a
 * configurable mapping derived from the application's JWT security configuration
 * (e.g. {@code JwtGrantedAuthoritiesConverter} settings), then checks the transformed
 * value against {@link Authentication#getAuthorities()}.
 *
 * <p>The mapping is built at construction time by inspecting relevant Spring Security beans
 * (see {@code SchemaSecurityAutoConfiguration}) so it reflects the actual JWT-to-authority
 * conversion in effect.
 *
 * <p>Example with mapping {@code {"feature:" → "FEATURE_", "role:" → "ROLE_"}}:
 * <ul>
 *   <li>scope {@code "feature:PRICING"} → strips {@code "feature:"} → prepends {@code "FEATURE_"}
 *       → checks authorities for {@code "FEATURE_PRICING"}</li>
 *   <li>scope {@code "role:ADMIN"} → strips {@code "role:"} → prepends {@code "ROLE_"}
 *       → checks authorities for {@code "ROLE_ADMIN"}</li>
 * </ul>
 *
 * <p>Prefix matching is performed in iteration order of the supplied map; the first matching
 * entry wins.
 */
public class ClaimPrefixMappingStrategy implements ScopeCheckStrategy {

    private final Map<String, String> prefixMappings;

    public ClaimPrefixMappingStrategy(Map<String, String> prefixMappings) {
        this.prefixMappings = prefixMappings;
    }

    @Override
    public boolean check(Authentication authentication, String scope) {
        if (authentication == null) return false;
        for (Map.Entry<String, String> entry : prefixMappings.entrySet()) {
            if (scope.startsWith(entry.getKey())) {
                String value = scope.substring(entry.getKey().length());
                String authority = entry.getValue() + value;
                return authentication.getAuthorities().stream()
                        .anyMatch(a -> Objects.requireNonNull(a.getAuthority()).equalsIgnoreCase(authority));
            }
        }
        return false;
    }
}
