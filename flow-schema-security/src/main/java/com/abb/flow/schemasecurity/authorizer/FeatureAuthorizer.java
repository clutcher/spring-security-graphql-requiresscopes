package com.abb.flow.schemasecurity.authorizer;

import org.springframework.security.core.Authentication;

public interface FeatureAuthorizer {

    boolean hasFeature(Authentication authentication, String feature);

}
