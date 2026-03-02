package com.abb.flow.schemasecurity.authorizer;

import org.springframework.security.core.Authentication;

public interface RoleAuthorizer {
    boolean hasRole(Authentication authentication, String role);
}