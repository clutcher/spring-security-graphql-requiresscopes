package com.abb.flow.schemasecurity.starter;

import com.abb.flow.schemasecurity.authorizer.DefaultFeatureAuthorizer;
import com.abb.flow.schemasecurity.authorizer.DefaultRoleAuthorizer;
import com.abb.flow.schemasecurity.authorizer.FeatureAuthorizer;
import com.abb.flow.schemasecurity.authorizer.RoleAuthorizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class SchemaSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FeatureAuthorizer.class)
    public FeatureAuthorizer featureAuthorizer() {
        return new DefaultFeatureAuthorizer();
    }

    @Bean
    @ConditionalOnMissingBean(RoleAuthorizer.class)
    public RoleAuthorizer roleAuthorizer(
            @Value("${spring.security.oauth2.resourceserver.jwt.authority-prefix:ROLE_}") String prefix
    ) {
        return new DefaultRoleAuthorizer(prefix);
    }

}