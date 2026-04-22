package dev.clutcher.security.graphql.starter;

import dev.clutcher.security.graphql.strategy.impl.ClaimPrefixMappingStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

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
