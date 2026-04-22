package dev.clutcher.security.graphql.starter;

import dev.clutcher.security.graphql.strategy.ScopeCheckStrategy;
import dev.clutcher.security.graphql.strategy.impl.ClaimPrefixMappingStrategy;
import dev.clutcher.security.graphql.strategy.impl.ScopePrefixAuthorityMatchStrategy;
import dev.clutcher.security.graphql.strategy.impl.SimpleAuthorityMatchStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaSecurityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SchemaSecurityAutoConfiguration.class))
            .withUserConfiguration(OAuth2PropertiesEnablingConfig.class);

    @Test
    void shouldRegisterSimpleAuthorityMatchStrategyBean() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(SimpleAuthorityMatchStrategy.class));
    }

    @Test
    void shouldRegisterClaimPrefixMappingStrategyBean() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(ClaimPrefixMappingStrategy.class));
    }

    @Test
    void shouldRegisterScopePrefixAuthorityMatchStrategyBeanWhenClaimNameUnset() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(ScopePrefixAuthorityMatchStrategy.class));
    }

    @Test
    void shouldRegisterScopePrefixAuthorityMatchStrategyBeanWhenClaimNameIsScope() {
        contextRunner
                .withPropertyValues("spring.security.oauth2.resourceserver.jwt.authorities-claim-name=scope")
                .run(context -> assertThat(context).hasSingleBean(ScopePrefixAuthorityMatchStrategy.class));
    }

    @Test
    void shouldRegisterScopePrefixAuthorityMatchStrategyBeanWhenClaimNameIsScp() {
        contextRunner
                .withPropertyValues("spring.security.oauth2.resourceserver.jwt.authorities-claim-name=scp")
                .run(context -> assertThat(context).hasSingleBean(ScopePrefixAuthorityMatchStrategy.class));
    }

    @Test
    void shouldSkipScopePrefixAuthorityMatchStrategyBeanWhenClaimNameIsRoles() {
        contextRunner
                .withPropertyValues("spring.security.oauth2.resourceserver.jwt.authorities-claim-name=roles")
                .run(context -> assertThat(context).doesNotHaveBean(ScopePrefixAuthorityMatchStrategy.class));
    }

    @Test
    void shouldBackOffWhenUserProvidesOwnJwtAuthenticationConverter() {
        contextRunner
                .withPropertyValues("spring.security.graphql.requiresscopes.scope-mappings.feature=FEATURE_")
                .withUserConfiguration(CustomJwtConverterConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtAuthenticationConverter.class);
                    assertThat(context.getBean(JwtAuthenticationConverter.class))
                            .isSameAs(context.getBean("customConverter"));
                });
    }

    @Test
    void shouldNotRegisterJwtAuthenticationConverterWithEmptyScopeMappings() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(JwtAuthenticationConverter.class));
    }

    @Test
    void shouldMatchApolloStyleScopeAgainstDefaultPrefixedAuthority() {
        contextRunner.run(context -> {
            Authentication auth = authWith("SCOPE_admin");
            assertThat(anyStrategyMatches(context, auth, "admin")).isTrue();
        });
    }

    @Test
    void shouldMatchVerbatimAuthorityValue() {
        contextRunner.run(context -> {
            Authentication auth = authWith("SCOPE_admin");
            assertThat(anyStrategyMatches(context, auth, "SCOPE_admin")).isTrue();
        });
    }

    @Test
    void shouldMatchConfiguredAuthorityPrefix() {
        contextRunner
                .withPropertyValues("spring.security.oauth2.resourceserver.jwt.authority-prefix=ACCESS_")
                .run(context -> {
                    Authentication auth = authWith("ACCESS_admin");
                    assertThat(anyStrategyMatches(context, auth, "admin")).isTrue();
                });
    }

    @Test
    void shouldNotMatchApolloScopeWhenClaimNameIsRoles() {
        contextRunner
                .withPropertyValues("spring.security.oauth2.resourceserver.jwt.authorities-claim-name=roles")
                .run(context -> {
                    Authentication auth = authWith("ROLE_ADMIN");
                    assertThat(anyStrategyMatches(context, auth, "admin")).isFalse();
                });
    }

    @Test
    void shouldMatchRoleScopeAgainstDefaultRolePrefix() {
        contextRunner.run(context -> {
            Authentication auth = authWith("ROLE_ADMIN");
            assertThat(anyStrategyMatches(context, auth, "role:ADMIN")).isTrue();
        });
    }

    @Test
    void shouldMatchScopeMappingsEntryOnTopOfDefaults() {
        contextRunner
                .withPropertyValues("spring.security.graphql.requiresscopes.scope-mappings.feature=FEATURE_")
                .run(context -> {
                    Authentication auth = authWith("SCOPE_admin", "FEATURE_PRICING");
                    assertThat(anyStrategyMatches(context, auth, "feature:PRICING")).isTrue();
                    assertThat(anyStrategyMatches(context, auth, "admin")).isTrue();
                });
    }

    @Test
    void shouldIncludeSpringSecurityDefaultConverterWhenScopeMappingsNonEmpty() {
        contextRunner
                .withPropertyValues("spring.security.graphql.requiresscopes.scope-mappings.feature=FEATURE_")
                .run(context -> {
                    JwtAuthenticationConverter converter = context.getBean(JwtAuthenticationConverter.class);
                    Jwt jwt = jwtWith(Map.of("scope", "read", "feature", List.of("PRICING")));

                    AbstractAuthenticationToken token = converter.convert(jwt);

                    assertThat(authorityNamesOf(token))
                            .contains("SCOPE_read", "FEATURE_PRICING");
                });
    }

    @Test
    void shouldRespectAuthorityPrefixPropertyInDefaultConverter() {
        contextRunner
                .withPropertyValues(
                        "spring.security.oauth2.resourceserver.jwt.authority-prefix=ACCESS_",
                        "spring.security.graphql.requiresscopes.scope-mappings.feature=FEATURE_"
                )
                .run(context -> {
                    JwtAuthenticationConverter converter = context.getBean(JwtAuthenticationConverter.class);
                    Jwt jwt = jwtWith(Map.of("scope", "read", "feature", List.of("PRICING")));

                    AbstractAuthenticationToken token = converter.convert(jwt);

                    assertThat(authorityNamesOf(token))
                            .contains("ACCESS_read", "FEATURE_PRICING");
                });
    }

    private Authentication authWith(String... authorities) {
        return new TestingAuthenticationToken("user", null, authorities);
    }

    private boolean anyStrategyMatches(AssertableApplicationContext context, Authentication auth, String scope) {
        Map<String, ScopeCheckStrategy> strategies = context.getBeansOfType(ScopeCheckStrategy.class);
        return strategies.values().stream().anyMatch(strategy -> strategy.check(auth, scope));
    }

    private Jwt jwtWith(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(c -> c.putAll(claims))
                .build();
    }

    private Collection<String> authorityNamesOf(AbstractAuthenticationToken token) {
        return token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OAuth2ResourceServerProperties.class)
    static class OAuth2PropertiesEnablingConfig {
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomJwtConverterConfig {
        @Bean
        JwtAuthenticationConverter customConverter() {
            JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter((Converter<Jwt, Collection<GrantedAuthority>>) jwt -> List.of());
            return converter;
        }
    }
}
