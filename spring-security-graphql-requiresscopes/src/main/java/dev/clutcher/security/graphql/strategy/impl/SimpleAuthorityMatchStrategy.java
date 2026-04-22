package dev.clutcher.security.graphql.strategy.impl;

import dev.clutcher.security.graphql.utils.AuthorityMatcher;
import dev.clutcher.security.graphql.strategy.ScopeCheckStrategy;
import org.springframework.security.core.Authentication;

public class SimpleAuthorityMatchStrategy implements ScopeCheckStrategy {

    @Override
    public boolean check(Authentication authentication, String scope) {
        return AuthorityMatcher.hasAuthority(authentication, scope);
    }
}
