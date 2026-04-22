package dev.clutcher.security.graphql.strategy;

import org.springframework.security.core.Authentication;

/**
 * Strategy for checking whether a scope string is satisfied for a given
 * {@link Authentication}.
 *
 * <p>Implementations are discovered by Spring and tried in injection order. The first
 * strategy that returns {@code true} short-circuits the check — the scope is considered
 * satisfied.
 *
 * <p>Register a custom strategy by declaring a Spring bean that implements this interface.
 * It is picked up automatically alongside the built-in strategies.
 */
public interface ScopeCheckStrategy {

    boolean check(Authentication authentication, String scope);
}
