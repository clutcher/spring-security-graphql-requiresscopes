package dev.clutcher.security.graphql;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

public class ClaimChecker {

    private final String claimName;

    public ClaimChecker(String claimName) {
        this.claimName = claimName;
    }

    public boolean has(Authentication authentication, String value) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return false;
        List<String> values = jwt.getToken().getClaimAsStringList(claimName);
        return values != null && values.stream().anyMatch(v -> v.equalsIgnoreCase(value));
    }
}