package dev.clutcher.security.graphql.strategy.impl;

import dev.clutcher.security.graphql.strategy.ScopeCheckStrategy;
import dev.clutcher.security.graphql.utils.AuthorityMatcher;
import org.springframework.security.core.Authentication;

/**
 * Bridges Apollo-style raw scope values to Spring Security's prefixed authority model.
 *
 * <p>Prepends a configured authority prefix (typically {@code "SCOPE_"}, read from
 * {@code spring.security.oauth2.resourceserver.jwt.authority-prefix}) to the incoming scope
 * string and delegates to {@link AuthorityMatcher#hasAuthority(Authentication, String)}.
 *
 * <p>The starter registers this strategy automatically only when Spring Security is reading the
 * OAuth2 {@code scope} claim (property {@code authorities-claim-name} is unset, {@code "scope"},
 * or {@code "scp"}). When the user retargets the JWT converter to a different claim, the
 * Apollo-shape semantic no longer applies and this strategy is not registered.
 */
public class ScopePrefixAuthorityMatchStrategy implements ScopeCheckStrategy {

    private final String authorityPrefix;

    public ScopePrefixAuthorityMatchStrategy(String authorityPrefix) {
        this.authorityPrefix = authorityPrefix;
    }

    @Override
    public boolean check(Authentication authentication, String scope) {
        return AuthorityMatcher.hasAuthority(authentication, authorityPrefix + scope);
    }
}
