package dev.clutcher.security.graphql.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public class AuthorityMatcher {

    private AuthorityMatcher() {
    }

    public static boolean   hasAuthority(Authentication authentication, String authorityValue) {
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
