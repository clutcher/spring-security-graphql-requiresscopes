package dev.clutcher.security.graphql.strategy;

import org.springframework.security.core.Authentication;

import java.util.Objects;

/**
 * Strategy 2: handles scopes with a type prefix (e.g. {@code "role:"}) by prepending
 * the Spring Security role prefix configured via {@code GrantedAuthorityDefaults}
 * (defaults to {@code "ROLE_"}) and checking the result against
 * {@link Authentication#getAuthorities()}.
 *
 * <p>Example: scope {@code "role:ADMIN"} → strips {@code "role:"} → prepends {@code "ROLE_"}
 * → checks authorities for {@code "ROLE_ADMIN"}.
 *
 * <p>The role prefix is read at construction time from the Spring Security
 * {@code GrantedAuthorityDefaults} bean so it stays in sync with the application's
 * security configuration.
 */
public class RolePrefixAuthorityMatchStrategy implements ScopeCheckStrategy {

    private static final String DEFAULT_SCOPE_PREFIX = "role:";
    private static final String DEFAULT_ROLE_PREFIX = "ROLE_";

    private final String scopePrefix;
    private final String authorityPrefix;

    public RolePrefixAuthorityMatchStrategy() {
        this(DEFAULT_SCOPE_PREFIX, DEFAULT_ROLE_PREFIX);
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
