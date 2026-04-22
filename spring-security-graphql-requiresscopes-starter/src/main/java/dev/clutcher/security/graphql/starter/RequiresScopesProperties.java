package dev.clutcher.security.graphql.starter;

import dev.clutcher.security.graphql.strategy.impl.ClaimPrefixMappingStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for the {@code @requiresScopes} scope-checking library.
 *
 * <p>{@code scope-mappings} is the single source of truth: the map key is both the
 * JWT claim name and the scope-type prefix used in {@code @requiresScopes} directives.
 * The map value is the Spring Security authority prefix. For example:
 *
 * <pre>{@code
 * spring:
 *   security:
 *     graphql:
 *       requiresscopes:
 *         scope-mappings:
 *           features: FEATURE_ # JWT claim "features" + scope "features:PRICING" → authority "FEATURE_PRICING"
 * }</pre>
 *
 * <p>The starter uses this map to auto-configure both:
 * <ul>
 *   <li>a {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter}
 *       that populates {@code Authentication.getAuthorities()} from each listed JWT claim, and
 *   <li>a {@link ClaimPrefixMappingStrategy} that checks
 *       those authorities when a {@code @requiresScopes} directive is evaluated.
 * </ul>
 *
 * <p>A {@code "role:"} entry is auto-registered by the starter using the prefix from
 * {@link org.springframework.security.config.core.GrantedAuthorityDefaults} (if a bean is present)
 * or Spring Security's default {@code "ROLE_"}. To customise the role prefix, declare a
 * {@code GrantedAuthorityDefaults} bean in your application.
 */
@ConfigurationProperties(prefix = "spring.security.graphql.requiresscopes")
public class RequiresScopesProperties {

    private Map<String, String> scopeMappings = new LinkedHashMap<>();

    public Map<String, String> getScopeMappings() {
        return scopeMappings;
    }

    public void setScopeMappings(Map<String, String> scopeMappings) {
        this.scopeMappings = scopeMappings;
    }
}
