package com.abb.flow.schemasecurity.starter;

import com.abb.flow.schemasecurity.directive.RequiresFeatureDirectiveWiring;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Registers {@code flow-schema-security} components with the Spring GraphQL runtime wiring.
 * <p>
 * Analogous to {@code SpringSecurityExceptionFilterConfigurer} — plugs the library
 * into the framework's extension point so consumers do not need any manual wiring.
 * <p>
 * Registers the {@code @requiresFeature} directive with the GraphQL schema runtime.
 */
public class SchemaSecurityConfigurer implements RuntimeWiringConfigurer {

    private final RequiresFeatureDirectiveWiring requiresFeatureDirectiveWiring;

    public SchemaSecurityConfigurer(RequiresFeatureDirectiveWiring requiresFeatureDirectiveWiring) {
        this.requiresFeatureDirectiveWiring = requiresFeatureDirectiveWiring;
    }

    @Override
    public void configure(graphql.schema.idl.RuntimeWiring.Builder builder) {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

}
