package dev.clutcher.security.graphql.strategy;

import org.springframework.security.core.Authentication;

import java.util.Objects;

/**
 * Strategy 2: strips a scope-type prefix (e.g. {@code "role:"}) prepends the configured
 * authority prefix (e.g. {@code "ROLE_"}), and checks the result against
 * {@link Authentication#getAuthorities()} (case-insensitive).
 *
 * <p>Example with default settings:
 * scope {@code "role:customergroup"} → strips {@code "role:"} → prepends {@code "ROLE_"}
 * → passes if authority {@code "ROLE_customergroup"} is present.
 *
 * <p>The authority prefix stays in sync with the application's {@code GrantedAuthorityDefaults}
 * bean when provided via the two-arg constructor.
 */
public class RolePrefixAuthorityMatchStrategy implements ScopeCheckStrategy {

    private static final String DEFAULT_SCOPE_PREFIX = "role:";
    private static final String DEFAULT_AUTHORITY_PREFIX = "ROLE_";

    private final String scopePrefix;
    private final String authorityPrefix;

    public RolePrefixAuthorityMatchStrategy() {
        this(DEFAULT_SCOPE_PREFIX, DEFAULT_AUTHORITY_PREFIX);
    }

    public RolePrefixAuthorityMatchStrategy(String scopePrefix, String authorityPrefix) {
        this.scopePrefix = scopePrefix;
        this.authorityPrefix = authorityPrefix;
    }

    @Override
    public boolean check(Authentication authentication, String scope) {
        if (authentication == null || !scope.startsWith(scopePrefix)) return false;
        String value = scope.substring(scopePrefix.length());
        String authority = authorityPrefix + value;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> Objects.requireNonNull(a.getAuthority()).equalsIgnoreCase(authority));
    }
}
