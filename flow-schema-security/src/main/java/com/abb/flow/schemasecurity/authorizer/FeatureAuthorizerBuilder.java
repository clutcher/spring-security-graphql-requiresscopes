package com.abb.flow.schemasecurity.authorizer;

import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Fluent builder for creating custom {@link FeatureAuthorizer} implementations.
 * <p>
 * Allows services to configure a {@link FeatureAuthorizer} with a custom
 * feature check function without depending on the default HTTP-based implementation.
 */
public class FeatureAuthorizerBuilder {

    private BiFunction<Authentication, String, Boolean> hasFeatureFunction;

    private FeatureAuthorizerBuilder() {
    }

    public static FeatureAuthorizerBuilder builder() {
        return new FeatureAuthorizerBuilder();
    }

    public FeatureAuthorizerBuilder checkFeatureWith(BiFunction<Authentication, String, Boolean> hasFeatureFunction) {
        this.hasFeatureFunction = hasFeatureFunction;
        return this;
    }

    public FeatureAuthorizer build() {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
