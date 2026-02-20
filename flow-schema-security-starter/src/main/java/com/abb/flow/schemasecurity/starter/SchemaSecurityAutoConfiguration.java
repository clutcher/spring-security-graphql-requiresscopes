package com.abb.flow.schemasecurity.starter;

import com.abb.flow.schemasecurity.authorizer.FeatureAuthorizer;
import com.abb.flow.schemasecurity.directive.RequiresFeatureDirectiveWiring;
import com.abb.flow.schemasecurity.provider.FeatureAccessProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Spring Boot auto-configuration for {@code flow-schema-security}.
 * <p>
 * Registers:
 * <ul>
 *   <li>A request-scoped {@link FeatureAuthorizer} bean backed by the user-management HTTP endpoint</li>
 *   <li>A {@link RequiresFeatureDirectiveWiring} bean for registering the {@code @requiresFeature} directive</li>
 *   <li>An HTTP client ({@link FeatureAccessProvider}) wired to the user-management service</li>
 * </ul>
 * All beans are conditional on missing — services can override any component.
 */
@AutoConfiguration
@EnableConfigurationProperties(SchemaSecurityProperties.class)
public class SchemaSecurityAutoConfiguration {

    @Bean
    @RequestScope
    @ConditionalOnMissingBean(FeatureAuthorizer.class)
    public FeatureAuthorizer featureAuthorizer(FeatureAccessProvider featureAccessProvider) {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Bean
    @ConditionalOnMissingBean(FeatureAccessProvider.class)
    public FeatureAccessProvider featureAccessProvider(SchemaSecurityProperties properties) {
        // TODO: implement — HTTP client calling /features/checkAccess on user-management
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Bean
    @ConditionalOnMissingBean(RequiresFeatureDirectiveWiring.class)
    public RequiresFeatureDirectiveWiring requiresFeatureDirectiveWiring(FeatureAuthorizer featureAuthorizer) {
        return new RequiresFeatureDirectiveWiring(featureAuthorizer);
    }

}
