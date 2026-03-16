package dev.clutcher.security.graphql.strategy;

import org.springframework.security.core.Authentication;

public interface ScopeCheckStrategy {

    boolean check(Authentication authentication, String scope);
}
