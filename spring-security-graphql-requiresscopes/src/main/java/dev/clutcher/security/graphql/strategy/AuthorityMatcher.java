package dev.clutcher.security.graphql.strategy;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Objects;

final class AuthorityMatcher {

    private AuthorityMatcher() {
    }

    static boolean   hasAuthority(Authentication authentication, String authorityValue) {
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
        return authority.equals(authorityValue) || authority.equals(authorityValue.toUpperCase());
    }
}
