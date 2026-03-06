package dev.clutcher.security.graphql.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for the {@code @requiresScopes} scope-checking library.
 *
 * <p>Use {@code requiresscopes.scope-mappings} in your {@code application.yml} to declare
 * how scope-type prefixes map to Spring Security authority prefixes:
 *
 * <pre>{@code
 * spring:
 *   security:
 *     graphql:
 *       requiresscopes:
 *         role-authority-prefix: ROLE_   # authority prefix used by RolePrefixAuthorityMatchStrategy
 *         scope-mappings:
 *           feature: FEATURE_            # "feature:PRICING" → checks for authority "FEATURE_PRICING"
 *           roles: ROLE_                 # "roles:ADMIN"    → checks for authority "ROLE_ADMIN"
 * }</pre>
 *
 * <p>The map key is the scope type name <em>without</em> the trailing colon; the library
 * appends it automatically.
 */
@ConfigurationProperties(prefix = "spring.security.graphql.requiresscopes")
public class RequiresScopesProperties {

    /**
     * Authority prefix prepended by {@code RolePrefixAuthorityMatchStrategy} when
     * checking {@code "role:"} scopes. Used as fallback when no
     * {@code GrantedAuthorityDefaults} bean is present. Defaults to {@code "ROLE_"}.
     */
    private String roleAuthorityPrefix = "ROLE_";

    /**
     * Maps scope type names (without the trailing {@code ":"}) to Spring Security
     * authority prefixes. Iteration order is preserved.
     */
    private Map<String, String> scopeMappings = new LinkedHashMap<>();

    public String getRoleAuthorityPrefix() {
        return roleAuthorityPrefix;
    }

    public void setRoleAuthorityPrefix(String roleAuthorityPrefix) {
        this.roleAuthorityPrefix = roleAuthorityPrefix;
    }

    public Map<String, String> getScopeMappings() {
        return scopeMappings;
    }

    public void setScopeMappings(Map<String, String> scopeMappings) {
        this.scopeMappings = scopeMappings;
    }
}
