package com.abb.flow.schemasecurity.authorizer;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

public class DefaultFeatureAuthorizer implements FeatureAuthorizer {

    @Override
    public boolean hasFeature(Authentication authentication, String feature) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return false;
        List<String> features = jwt.getToken().getClaimAsStringList("enabledFeatures");
        return features != null && features.contains(feature);
    }
}