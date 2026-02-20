package com.abb.flow.schemasecurity.provider.functions;

/**
 * Utility class providing pre-built functions for common feature access scenarios.
 * <p>
 * Mirrors {@code ExceptionMappingFunctions} from {@code spring-security-exception-handler}.
 * Provides factory methods for constructing standard {@link AccountIdentityResolver}
 * instances covering the most common argument shapes in the GraphQL schema.
 */
public class FeatureAccessFunctions {

    private FeatureAccessFunctions() {
    }

    /**
     * Resolves {@code accountId} from a direct GraphQL argument of type {@code AccountIdentityInput}.
     */
    public static AccountIdentityResolver directAccountIdArgument() {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Resolves {@code accountIds} (list) from a filter input argument, returning the first account.
     * Used for queries where {@code accountIds} is nested inside a filter object.
     */
    public static AccountIdentityResolver accountIdsFromFilterArgument() {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

}
