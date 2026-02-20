package com.abb.flow.schemasecurity.provider.functions;

import graphql.schema.DataFetchingEnvironment;

/**
 * Extracts an account identity from a GraphQL {@link DataFetchingEnvironment}.
 * <p>
 * Used by {@link com.abb.flow.schemasecurity.directive.RequiresFeatureDirectiveWiring}
 * to resolve the {@code accountId} argument from different argument shapes
 * (e.g. directly as {@code accountId}, or nested inside a filter input).
 */
@FunctionalInterface
public interface AccountIdentityResolver {

    Object resolve(DataFetchingEnvironment environment, String argumentName);

}
