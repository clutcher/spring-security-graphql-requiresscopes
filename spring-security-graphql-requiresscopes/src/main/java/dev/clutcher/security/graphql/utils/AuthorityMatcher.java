package dev.clutcher.security.graphql.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Shared authority-comparison utility used by all built-in {@link dev.clutcher.security.graphql.strategy.ScopeCheckStrategy}
 * implementations.
 *
 * <p>The matching rule is strict string equality. This aligns with:
 * <ul>
 *   <li>RFC 6749 §3.3, which defines OAuth 2.0 scope values as case-sensitive strings,</li>
 *   <li>Apollo Router's reference implementation of {@code @requiresScopes}, which performs case-sensitive
 *       set-containment checks, and</li>
 *   <li>Spring Security's {@code AuthoritiesAuthorizationManager}, which compares authorities via
 *       {@link java.util.Collection#contains(Object)} (case-sensitive {@link String#equals(Object)}).</li>
 * </ul>
 *
 * <p>Centralising the rule here means a policy change is applied in one place.
 */
public class AuthorityMatcher {

    private AuthorityMatcher() {
    }

    /**
     * Returns {@code true} if {@code authentication} has an authority that equals {@code authorityValue} exactly.
     * Returns {@code false} when {@code authentication} is {@code null}.
     */
    public static boolean hasAuthority(Authentication authentication, String authorityValue) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> isSameAuthority(authorityValue, grantedAuthority));
    }

    private static boolean isSameAuthority(String authorityValue, GrantedAuthority grantedAuthority) {
        String authority = grantedAuthority.getAuthority();
        if (authority == null || authorityValue == null) {
            return false;
        }
        return authority.equals(authorityValue);
    }
}
