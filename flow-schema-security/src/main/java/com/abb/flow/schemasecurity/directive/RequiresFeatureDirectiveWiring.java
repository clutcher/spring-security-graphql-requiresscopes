package com.abb.flow.schemasecurity.directive;

import com.abb.flow.schemasecurity.authorizer.FeatureAuthorizer;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;

/**
 * GraphQL schema directive wiring for {@code @requiresFeature}.
 * <p>
 * Wraps field data fetchers with a feature access check, using {@link FeatureAuthorizer}
 * to verify that the authenticated user has the required feature for the requested account
 * before resolving the field.
 * <p>
 * Schema usage:
 * <pre>
 *   directive @requiresFeature(feature: String!, accountIdArgument: String = "accountId")
 *       on FIELD_DEFINITION
 *
 *   type Mutation {
 *       deleteCarts(filter: DeleteCartFilterInput!): MutationResult
 *           @requiresFeature(feature: "CART_SECTION", accountIdArgument: "filter")
 *   }
 * </pre>
 */
public class RequiresFeatureDirectiveWiring implements SchemaDirectiveWiring {

    private final FeatureAuthorizer featureAuthorizer;

    public RequiresFeatureDirectiveWiring(FeatureAuthorizer featureAuthorizer) {
        this.featureAuthorizer = featureAuthorizer;
    }

    @Override
    public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
