package com.abb.flow.schemasecurity.provider;

/**
 * SPI for performing the actual feature access check.
 * <p>
 * Implementations are responsible for resolving whether a given user
 * has a specific feature enabled for a given account. The default implementation
 * calls the {@code user-management} service via HTTP.
 * <p>
 * Mirrors the existing {@code AccessCheckProvider} pattern used for account access.
 */
public interface FeatureAccessProvider {

    /**
     * Returns true if the user identified by {@code userId} has the given {@code feature}
     * enabled for the account identified by {@code accountUid}.
     */
    boolean hasAccess(String userId, Object accountUid, String feature);

}
