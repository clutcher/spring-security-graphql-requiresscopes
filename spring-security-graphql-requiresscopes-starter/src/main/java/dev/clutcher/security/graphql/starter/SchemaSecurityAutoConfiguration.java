package dev.clutcher.security.graphql.starter;

import dev.clutcher.security.graphql.instrumentation.RequiresScopesInstrumentation;
import dev.clutcher.security.graphql.strategy.ScopeCheckStrategy;
import dev.clutcher.security.graphql.strategy.impl.ClaimPrefixMappingStrategy;
import dev.clutcher.security.graphql.strategy.impl.ScopePrefixAuthorityMatchStrategy;
import dev.clutcher.security.graphql.strategy.impl.SimpleAuthorityMatchStrategy;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.type.AnnotatedTypeMetadata;
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
@EnableConfigurationProperties({RequiresScopesProperties.class, OAuth2ResourceServerProperties.class})
public class SchemaSecurityAutoConfiguration {

    private static final String AUTHORITIES_CLAIM_NAME_PROPERTY =
            "spring.security.oauth2.resourceserver.jwt.authorities-claim-name";
    private static final String SCOPE_MAPPINGS_PROPERTY =
            "spring.security.graphql.requiresscopes.scope-mappings";
    private static final String DEFAULT_AUTHORITY_PREFIX = "SCOPE_";
    private static final String DEFAULT_ROLE_PREFIX = "ROLE_";

    @Bean
    @ConditionalOnMissingBean(SimpleAuthorityMatchStrategy.class)
    public SimpleAuthorityMatchStrategy simpleAuthorityMatchStrategy() {
        return new SimpleAuthorityMatchStrategy();
    }

    @Bean
    @ConditionalOnMissingBean(ScopePrefixAuthorityMatchStrategy.class)
    @Conditional(OAuth2ScopeClaimCondition.class)
    public ScopePrefixAuthorityMatchStrategy scopePrefixAuthorityMatchStrategy(
            OAuth2ResourceServerProperties oauth2Properties) {
        String configuredPrefix = oauth2Properties.getJwt().getAuthorityPrefix();
        String authorityPrefix = configuredPrefix != null ? configuredPrefix : DEFAULT_AUTHORITY_PREFIX;
        return new ScopePrefixAuthorityMatchStrategy(authorityPrefix);
    }

    @Bean
    @ConditionalOnMissingBean(ClaimPrefixMappingStrategy.class)
    public ClaimPrefixMappingStrategy claimPrefixMappingStrategy(
            Optional<GrantedAuthorityDefaults> grantedAuthorityDefaults,
            RequiresScopesProperties properties) {

        String rolePrefix = grantedAuthorityDefaults
                .map(GrantedAuthorityDefaults::getRolePrefix)
                .orElse(DEFAULT_ROLE_PREFIX);

        Map<String, String> mappings = createClaimPrefixMapping(properties, rolePrefix);

        return new ClaimPrefixMappingStrategy(mappings);
    }

    @Bean
    @ConditionalOnMissingBean(JwtAuthenticationConverter.class)
    @Conditional(NonEmptyScopeMappingsCondition.class)
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            RequiresScopesProperties properties,
            OAuth2ResourceServerProperties oauth2Properties) {
        List<Converter<Jwt, Collection<GrantedAuthority>>> converters = new ArrayList<>();
        converters.add(buildDefaultAuthoritiesConverter(oauth2Properties));
        converters.addAll(buildScopeMappingConverters(properties));

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(composeAuthoritiesConverters(converters));
        return jwtConverter;
    }

    private static @NonNull Map<String, String> createClaimPrefixMapping(
            RequiresScopesProperties properties,
            String rolePrefix
    ) {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("role:", rolePrefix);
        properties.getScopeMappings().forEach((key, value) -> mappings.put(key + ":", value));
        return mappings;
    }

    private static Converter<Jwt, Collection<GrantedAuthority>> buildDefaultAuthoritiesConverter(
            OAuth2ResourceServerProperties oauth2Properties) {
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        OAuth2ResourceServerProperties.Jwt jwt = oauth2Properties.getJwt();
        if (jwt.getAuthoritiesClaimName() != null) {
            converter.setAuthoritiesClaimName(jwt.getAuthoritiesClaimName());
        }
        if (jwt.getAuthorityPrefix() != null) {
            converter.setAuthorityPrefix(jwt.getAuthorityPrefix());
        }
        if (jwt.getAuthoritiesClaimDelimiter() != null) {
            converter.setAuthoritiesClaimDelimiter(jwt.getAuthoritiesClaimDelimiter());
        }
        return converter;
    }

    private static List<Converter<Jwt, Collection<GrantedAuthority>>> buildScopeMappingConverters(
            RequiresScopesProperties properties) {
        List<Converter<Jwt, Collection<GrantedAuthority>>> converters = new ArrayList<>();
        properties.getScopeMappings().forEach((claimName, authorityPrefix) -> {
            JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
            converter.setAuthoritiesClaimName(claimName);
            converter.setAuthorityPrefix(authorityPrefix);
            converters.add(converter);
        });
        return converters;
    }

    private static Converter<Jwt, Collection<GrantedAuthority>> composeAuthoritiesConverters(
            List<Converter<Jwt, Collection<GrantedAuthority>>> converters) {
        return jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            converters.forEach(converter -> authorities.addAll(converter.convert(jwt)));
            return authorities;
        };
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

    static class OAuth2ScopeClaimCondition extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String claimName = context.getEnvironment().getProperty(AUTHORITIES_CLAIM_NAME_PROPERTY);
            if (claimName == null || "scope".equals(claimName) || "scp".equals(claimName)) {
                return ConditionOutcome.match(
                        "authorities-claim-name is " + (claimName == null ? "unset" : claimName));
            }
            return ConditionOutcome.noMatch(
                    "authorities-claim-name is '" + claimName + "' — not an OAuth2 scope claim");
        }
    }

    static class NonEmptyScopeMappingsCondition extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Map<String, String> mappings = Binder.get(context.getEnvironment())
                    .bind(SCOPE_MAPPINGS_PROPERTY, Bindable.mapOf(String.class, String.class))
                    .orElseGet(Map::of);
            if (mappings.isEmpty()) {
                return ConditionOutcome.noMatch("scope-mappings is empty");
            }
            return ConditionOutcome.match("scope-mappings has " + mappings.size() + " entries");
        }
    }
}
