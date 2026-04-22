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

    @Bean
    @ConditionalOnMissingBean(SimpleAuthorityMatchStrategy.class)
    public SimpleAuthorityMatchStrategy simpleAuthorityMatchStrategy() {
        return new SimpleAuthorityMatchStrategy();
    }

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

    @Bean
    @ConditionalOnMissingBean(ClaimPrefixMappingStrategy.class)
    public ClaimPrefixMappingStrategy claimPrefixMappingStrategy(RequiresScopesProperties properties) {
        Map<String, String> mappings = new LinkedHashMap<>();
        properties.getScopeMappings().forEach((key, value) -> mappings.put(key + ":", value));
        return new ClaimPrefixMappingStrategy(mappings);
    }

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
