package dev.clutcher.security.graphql.instrumentation;

import dev.clutcher.security.graphql.strategy.ScopeCheckStrategy;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.language.ArrayValue;
import graphql.language.StringValue;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;

import java.util.List;

/**
 * Enforces {@code @requiresScopes} directives at field-execution time using OR-of-AND
 * scope evaluation. {@link Authentication} is read from the GraphQL context under key
 * {@code SecurityContext.class.getName()}.
 */
public class RequiresScopesInstrumentation extends SimplePerformantInstrumentation {

    private static final String DIRECTIVE_NAME = "requiresScopes";
    private static final String SCOPES_ARGUMENT = "scopes";

    private final List<ScopeCheckStrategy> strategies;

    public RequiresScopesInstrumentation(List<ScopeCheckStrategy> strategies) {
        this.strategies = strategies;
    }

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

        List<List<String>> scopes = extractScopes(directive);

        return environment -> {
            Authentication authentication = resolveAuthentication(environment);
            enforceScopes(authentication, scopes);
            return dataFetcher.get(environment);
        };
    }

    private Authentication resolveAuthentication(DataFetchingEnvironment environment) {
        SecurityContext securityContext = environment.getGraphQlContext().get(SecurityContext.class.getName());
        return securityContext != null ? securityContext.getAuthentication() : null;
    }

    private List<List<String>> extractScopes(GraphQLAppliedDirective directive) {
        GraphQLAppliedDirectiveArgument argument = directive.getArgument(SCOPES_ARGUMENT);
        var valueState = argument.getArgumentValue();

        if (valueState.isLiteral() && valueState.getValue() instanceof ArrayValue outer) {
            return readLiteralGroups(outer);
        }
        if (valueState.isExternal() && valueState.getValue() instanceof List<?> outer) {
            return readExternalGroups(outer);
        }
        return List.of();
    }

    private List<List<String>> readLiteralGroups(ArrayValue outer) {
        return outer.getValues().stream()
                .filter(ArrayValue.class::isInstance)
                .map(ArrayValue.class::cast)
                .map(this::readLiteralGroup)
                .toList();
    }

    private List<String> readLiteralGroup(ArrayValue group) {
        return group.getValues().stream()
                .filter(StringValue.class::isInstance)
                .map(value -> ((StringValue) value).getValue())
                .toList();
    }

    private List<List<String>> readExternalGroups(List<?> outer) {
        return outer.stream()
                .filter(List.class::isInstance)
                .map(List.class::cast)
                .map(this::readExternalGroup)
                .toList();
    }

    private List<String> readExternalGroup(List<?> group) {
        return group.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private void enforceScopes(Authentication authentication, List<List<String>> scopes) {
        for (List<String> andGroup : scopes) {
            if (andGroup.stream().allMatch(scope -> checkScope(authentication, scope))) {
                return;
            }
        }
        throw new AccessDeniedException("Access denied: insufficient scopes");
    }

    private boolean checkScope(Authentication authentication, String scope) {
        return strategies.stream().anyMatch(strategy -> strategy.check(authentication, scope));
    }
}
