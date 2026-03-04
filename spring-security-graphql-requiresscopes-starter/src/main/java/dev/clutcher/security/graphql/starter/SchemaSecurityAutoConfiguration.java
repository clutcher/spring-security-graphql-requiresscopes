package dev.clutcher.security.graphql.starter;

import dev.clutcher.security.graphql.ClaimChecker;
import dev.clutcher.security.graphql.instrumentation.RequiresScopesInstrumentation;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
public class SchemaSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "featureChecker")
    public ClaimChecker featureChecker() {
        return new ClaimChecker("enabledFeatures");
    }

    @Bean
    @ConditionalOnMissingBean(name = "roleChecker")
    public ClaimChecker roleChecker() {
        return new ClaimChecker("roles");
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
                ClaimChecker featureChecker,
                ClaimChecker roleChecker
        ) {
            return new RequiresScopesInstrumentation(featureChecker, roleChecker);
        }
    }

}