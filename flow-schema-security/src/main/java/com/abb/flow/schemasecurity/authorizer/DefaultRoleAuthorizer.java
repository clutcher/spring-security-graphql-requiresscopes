package com.abb.flow.schemasecurity.authorizer;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Objects;

public class DefaultRoleAuthorizer implements RoleAuthorizer {

    private final String rolePrefix;

    public DefaultRoleAuthorizer(String rolePrefix) {
        this.rolePrefix = rolePrefix;
    }

    @Override
    public boolean hasRole(Authentication authentication, String role) {
        if (authentication == null) return false;
        String required = rolePrefix + role.toLowerCase();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .anyMatch(a -> a.equalsIgnoreCase(required));
    }

}