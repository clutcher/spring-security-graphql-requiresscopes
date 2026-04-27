package dev.clutcher.security.graphql.instrumentation;

import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLAppliedDirective;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;

/**
 * Enforces the {@code @authenticated} directive at field-execution time.
 * Denies access when the caller is anonymous or has no {@link SecurityContext}.
 */
public class AuthenticatedInstrumentation extends SimplePerformantInstrumentation {

    private static final String DIRECTIVE_NAME = "authenticated";

    @Override
    public DataFetcher<?> instrumentDataFetcher(
            DataFetcher<?> dataFetcher,
            InstrumentationFieldFetchParameters params,
            InstrumentationState state) {

        GraphQLAppliedDirective directive = params.getEnvironment()
                .getFieldDefinition()
                .getAppliedDirective(DIRECTIVE_NAME);

        if (directive == null) {
            return dataFetcher;
        }

        return environment -> {
            enforceAuthenticated(resolveAuthentication(environment));
            return dataFetcher.get(environment);
        };
    }

    private Authentication resolveAuthentication(DataFetchingEnvironment environment) {
        SecurityContext ctx = environment.getGraphQlContext().get(SecurityContext.class.getName());
        return ctx != null ? ctx.getAuthentication() : null;
    }

    private void enforceAuthenticated(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Access denied: authentication required");
        }
    }
}
