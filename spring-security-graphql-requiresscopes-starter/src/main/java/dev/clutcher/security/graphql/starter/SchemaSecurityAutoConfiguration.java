package dev.clutcher.security.graphql.starter;

import dev.clutcher.security.graphql.instrumentation.RequiresScopesInstrumentation;
import dev.clutcher.security.graphql.strategy.ClaimPrefixMappingStrategy;
import dev.clutcher.security.graphql.strategy.RolePrefixAuthorityMatchStrategy;
import dev.clutcher.security.graphql.strategy.ScopeCheckStrategy;
import dev.clutcher.security.graphql.strategy.SimpleAuthorityMatchStrategy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.core.GrantedAuthorityDefaults;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@AutoConfiguration
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
     * Strategy 2: strips the {@code "role:"} prefix, prepends the Spring Security role prefix
     * (read from {@link GrantedAuthorityDefaults} if present, otherwise defaults to {@code "ROLE_"}),
     * and checks authorities.
     */
    @Bean
    @ConditionalOnMissingBean(RolePrefixAuthorityMatchStrategy.class)
    public RolePrefixAuthorityMatchStrategy rolePrefixAuthorityMatchStrategy(
            Optional<GrantedAuthorityDefaults> grantedAuthorityDefaults) {
        String rolePrefix = grantedAuthorityDefaults
                .map(GrantedAuthorityDefaults::getRolePrefix)
                .orElse("ROLE_");
        return new RolePrefixAuthorityMatchStrategy("role:", rolePrefix);
    }

    /**
     * Strategy 3: transforms scopes like {@code "feature:PRICING"} into {@code "FEATURE_PRICING"}
     * using a configurable scope-prefix → authority-prefix map, then checks authorities.
     *
     * <p>Defaults to {@code {"feature:" → "FEATURE_", "role:" → "ROLE_"}}. Override by
     * providing a {@code ClaimPrefixMappingStrategy} bean with the mappings that match
     * how your {@code JwtGrantedAuthoritiesConverter} is configured.
     */
    @Bean
    @ConditionalOnMissingBean(ClaimPrefixMappingStrategy.class)
    public ClaimPrefixMappingStrategy claimPrefixMappingStrategy() {
        return new ClaimPrefixMappingStrategy(Map.of("feature:", "FEATURE_", "role:", "ROLE_"));
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
