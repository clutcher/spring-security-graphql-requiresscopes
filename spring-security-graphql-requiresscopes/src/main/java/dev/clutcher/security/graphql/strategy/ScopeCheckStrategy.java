package dev.clutcher.security.graphql.strategy;

import org.springframework.security.core.Authentication;

/**
 * Strategy for checking whether a given scope is satisfied by the current {@link Authentication}.
 *
 * <p>Implementations decide how to interpret the scope string and what part of the
 * {@code Authentication} to inspect (e.g. Spring Security authorities).
 *
 * <p>A scope check passes if at least one registered strategy returns {@code true}.
 */
public interface ScopeCheckStrategy {

    boolean check(Authentication authentication, String scope);
}
