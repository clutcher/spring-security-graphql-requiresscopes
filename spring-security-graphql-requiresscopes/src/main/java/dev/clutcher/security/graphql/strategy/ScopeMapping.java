package dev.clutcher.security.graphql.strategy;

/**
 * Defines a mapping from a scope-type prefix to a Spring Security authority prefix
 * for use with {@link ClaimPrefixMappingStrategy}.
 *
 * <p>Register one or more beans of this type in your application context to tell the
 * library how to transform scope values into Spring Security authority strings.
 *
 * <p>Example: given {@code new ScopeMapping("feature:", "FEATURE_")}, the scope
 * {@code "feature:PRICING"} will be checked as authority {@code "FEATURE_PRICING"}.
 *
 * @param scopeTypePrefix  the prefix used in the {@code @requiresScopes} directive value
 *                         (e.g. {@code "feature:"})
 * @param authorityPrefix  the corresponding Spring Security authority prefix produced by
 *                         your {@code JwtGrantedAuthoritiesConverter} configuration
 *                         (e.g. {@code "FEATURE_"})
 */
public record ScopeMapping(String scopeTypePrefix, String authorityPrefix) {
}
