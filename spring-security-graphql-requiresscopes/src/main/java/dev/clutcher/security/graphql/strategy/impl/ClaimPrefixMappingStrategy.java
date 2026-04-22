package dev.clutcher.security.graphql.strategy.impl;

import dev.clutcher.security.graphql.utils.AuthorityMatcher;
import dev.clutcher.security.graphql.strategy.ScopeCheckStrategy;
import org.springframework.security.core.Authentication;

import java.util.Map;

public class ClaimPrefixMappingStrategy implements ScopeCheckStrategy {

    private final Map<String, String> prefixMappings;

    public ClaimPrefixMappingStrategy(Map<String, String> prefixMappings) {
        this.prefixMappings = prefixMappings;
    }

    @Override
    public boolean check(Authentication authentication, String scope) {
        for (Map.Entry<String, String> entry : prefixMappings.entrySet()) {
            if (scope.startsWith(entry.getKey())) {
                String authority = entry.getValue() + scope.substring(entry.getKey().length());
                return AuthorityMatcher.hasAuthority(authentication, authority);
            }
        }
        return false;
    }
}
