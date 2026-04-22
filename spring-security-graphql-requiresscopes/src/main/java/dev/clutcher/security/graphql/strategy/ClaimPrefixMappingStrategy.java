package dev.clutcher.security.graphql.strategy;

import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Objects;

/**
 * Strategy 3: maps scope-type prefixes to Spring Security authority prefixes, transforms
 * the scope value, and checks the result against {@link Authentication#getAuthorities()}
 * (case-insensitive).
 *
 * <p>The mapping is supplied at construction time and built from the
 * {@code spring.security.graphql.requiresscopes.scope-mappings} property — the library
 * itself makes no assumptions about which claim names or authority prefixes your application uses.
 *
 * <p>Example: given mapping {@code {"mytype:" → "MYTYPE_"}}, scope {@code "mytype:FOO"}
 * → strips {@code "mytype:"} → prepends {@code "MYTYPE_"} → checks for authority
 * {@code "MYTYPE_FOO"}.
 *
 * <p>Prefix matching is performed in iteration order; the first matching entry wins.
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
