package dev.clutcher.security.graphql.strategy;

import org.springframework.security.core.Authentication;

import java.util.Objects;

/**
 * Strategy 1: checks whether the scope value exists as-is in
 * {@link Authentication#getAuthorities()} (case-insensitive).
 *
 * <p>Example: scope {@code "feature:PRICING"} passes if the authentication contains
 * a granted authority whose value is exactly {@code "feature:PRICING"}.
 *
 * <p>Useful when the {@code JwtAuthenticationConverter} is configured to produce
 * authorities that already include the scope-type prefix.
 */
public class SimpleAuthorityMatchStrategy implements ScopeCheckStrategy {

    @Override
    public boolean check(Authentication authentication, String scope) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> Objects.requireNonNull(a.getAuthority()).equalsIgnoreCase(scope));
    }
}
