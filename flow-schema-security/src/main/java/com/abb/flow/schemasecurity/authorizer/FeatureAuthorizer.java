package com.abb.flow.schemasecurity.authorizer;

import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * Main interface for feature-based authorization checks on the GraphQL schema layer.
 * <p>
 * Mirrors the existing {@code AccessAuthorizer} pattern used for account access checks.
 * Intended to be used in {@code @PreAuthorize} expressions:
 * <pre>
 *   @PreAuthorize("@featureAuthorizer.hasFeature(authentication, #accountId, 'PRODUCT_CERTIFICATES_SECTION')")
 * </pre>
 */
public interface FeatureAuthorizer {

    /**
     * Checks if the authenticated user has the required feature enabled for the given account.
     */
    boolean hasFeature(Authentication authentication, Object accountId, String feature);

    /**
     * Checks if the authenticated user has the required feature enabled for any of the given accounts.
     * Used for multi-account queries.
     */
    boolean hasAnyFeature(Authentication authentication, List<?> accountIds, String feature);

}
