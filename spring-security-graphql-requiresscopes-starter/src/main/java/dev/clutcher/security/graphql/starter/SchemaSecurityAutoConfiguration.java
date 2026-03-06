package dev.clutcher.security.graphql.starter;

import dev.clutcher.security.graphql.instrumentation.RequiresScopesInstrumentation;
import dev.clutcher.security.graphql.strategy.ClaimPrefixMappingStrategy;
import dev.clutcher.security.graphql.strategy.RolePrefixAuthorityMatchStrategy;
import dev.clutcher.security.graphql.strategy.ScopeCheckStrategy;
import dev.clutcher.security.graphql.strategy.SimpleAuthorityMatchStrategy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@AutoConfiguration
@EnableConfigurationProperties(RequiresScopesProperties.class)
public class SchemaSecurityAutoConfiguration {

    /**
     * Strategy 1: checks the exact scope value against Spring Security authorities.
     */
    @Bean
    @ConditionalOnMissingBean(SimpleAuthorityMatchStrategy.class)
    public SimpleAuthorityMatchStrategy simpleAuthorityMatchStrategy() {
        return new SimpleAuthorityMatchStrategy();
    }

    /**
     * Strategy 2: strips the {@code "role:"} scope-type prefix, prepends the Spring Security
     * role prefix read from {@link GrantedAuthorityDefaults} (defaults to {@code "ROLE_"}),
     * and checks the result against the authentication authorities.
     *
     * <p>The authority prefix is kept in sync with the application's
     * {@link GrantedAuthorityDefaults} bean, so it reflects whatever role prefix the
     * application has configured in Spring Security.
     */
    @Bean
    @ConditionalOnMissingBean(RolePrefixAuthorityMatchStrategy.class)
    public RolePrefixAuthorityMatchStrategy rolePrefixAuthorityMatchStrategy(
            Optional<GrantedAuthorityDefaults> grantedAuthorityDefaults,
            RequiresScopesProperties properties) {
        String rolePrefix = grantedAuthorityDefaults
                .map(GrantedAuthorityDefaults::getRolePrefix)
                .orElseGet(properties::getRoleAuthorityPrefix);
        return new RolePrefixAuthorityMatchStrategy("role:", rolePrefix);
    }

    /**
     * Strategy 3: builds a scope-prefix → authority-prefix mapping from
     * {@code requiresscopes.scope-mappings} properties, then transforms scope values
     * and checks the result against the authentication authorities.
     *
     * <p>Configure in {@code application.yml} — no beans required:
     * <pre>{@code
     * spring:
     *   security:
     *     graphql:
     *       requiresscopes:
     *         scope-mappings:
     *           feature: FEATURE_   # "feature:PRICING" → checks authority "FEATURE_PRICING"
     *           roles: ROLE_        # "roles:ADMIN"    → checks authority "ROLE_ADMIN"
     * }</pre>
     */
    @Bean
    @ConditionalOnMissingBean(ClaimPrefixMappingStrategy.class)
    public ClaimPrefixMappingStrategy claimPrefixMappingStrategy(RequiresScopesProperties properties) {
        Map<String, String> mappings = new LinkedHashMap<>();
        properties.getScopeMappings().forEach((key, value) -> mappings.put(key + ":", value));
        return new ClaimPrefixMappingStrategy(mappings);
    }

    /**
     * Configures a {@link JwtAuthenticationConverter} that populates
     * {@code Authentication.getAuthorities()} from every JWT claim listed in
     * {@code requiresscopes.scope-mappings}.
     *
     * <p>Because {@code scope-mappings} is the single source of truth (map key = JWT claim
     * name = scope-type prefix, map value = authority prefix), no separate
     * {@code jwt-claim-mappings} property is needed:
     * <pre>{@code
     * scope-mappings:
     *   roles: ROLE_       # JWT "roles": ["ADMIN"]   → authority "ROLE_ADMIN"
     *   features: FEATURE_ # JWT "features": ["PRICING"] → authority "FEATURE_PRICING"
     * }</pre>
     *
     * <p>Only registered when no {@link JwtAuthenticationConverter} bean exists yet.
     * When registered, it replaces Spring Boot's auto-configured converter, so
     * {@code spring.security.oauth2.resourceserver.jwt.authorities-claim-name} is ignored.
     */
    @Bean
    @ConditionalOnMissingBean(JwtAuthenticationConverter.class)
    public JwtAuthenticationConverter jwtAuthenticationConverter(RequiresScopesProperties properties) {
        List<Converter<Jwt, Collection<GrantedAuthority>>> converters = new ArrayList<>();
        properties.getScopeMappings().forEach((claimName, authorityPrefix) -> {
            JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
            converter.setAuthoritiesClaimName(claimName);
            converter.setAuthorityPrefix(authorityPrefix);
            converters.add(converter);
        });

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            converters.forEach(c -> authorities.addAll(c.convert(jwt)));
            return authorities;
        });
        return jwtConverter;
    }

    /**
     * Isolated in a nested class so that the outer {@code SchemaSecurityAutoConfiguration}
     * can be loaded by REST-only services without graphql-java on the classpath.
     * Spring Boot only loads this inner class when the condition is satisfied.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "graphql.execution.instrumentation.Instrumentation")
    static class GraphQLInstrumentationConfiguration {

        @Bean
        @ConditionalOnMissingBean(RequiresScopesInstrumentation.class)
        public RequiresScopesInstrumentation requiresScopesInstrumentation(
                List<ScopeCheckStrategy> strategies) {
            return new RequiresScopesInstrumentation(strategies);
        }
    }
}
