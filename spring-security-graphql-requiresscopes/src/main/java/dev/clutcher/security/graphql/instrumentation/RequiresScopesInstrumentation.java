package dev.clutcher.security.graphql.instrumentation;

import dev.clutcher.security.graphql.strategy.ScopeCheckStrategy;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.language.ArrayValue;
import graphql.language.StringValue;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;

import java.util.List;

/**
 * Enforces {@code @requiresScopes} directives declared in the GraphQL schema at field-execution
 * time — no per-controller annotations required.
 *
 * <p>For each field fetch, the instrumentation checks whether the resolved field carries a
 * {@code @requiresScopes} applied directive. If it does, the scope values are evaluated
 * against the current {@link Authentication} using a list of {@link ScopeCheckStrategy}
 * instances. A scope passes if <em>any</em> strategy returns {@code true} for it.
 *
 * <p>The {@code scopes} argument follows Apollo Federation semantics:
 * <ul>
 *   <li>Outer array = OR — at least one inner group must pass.</li>
 *   <li>Inner array = AND — every scope in a group must be satisfied.</li>
 * </ul>
 *
 * Example: {@code @requiresScopes(scopes: [["feature:X", "role:Y"]])} requires the user to
 * have <em>both</em> feature X <em>and</em> role Y.
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

        return env -> {
            SecurityContext securityContext = env.getGraphQlContext().get(SecurityContext.class.getName());
            Authentication authentication = securityContext != null ? securityContext.getAuthentication() : null;
            enforceScopes(authentication, scopes);
            return dataFetcher.get(env);
        };
    }

    /**
     * Extracts the {@code [[String]]} scopes value from the directive argument.
     * Handles both SDL literal values (parsed as graphql-java AST nodes) and
     * already-coerced external values.
     */
    private List<List<String>> extractScopes(GraphQLAppliedDirective directive) {
        GraphQLAppliedDirectiveArgument arg = directive.getArgument(SCOPES_ARGUMENT);
        var valueState = arg.getArgumentValue();

        if (valueState.isLiteral() && valueState.getValue() instanceof ArrayValue outerArray) {
            return outerArray.getValues().stream()
                    .filter(ArrayValue.class::isInstance)
                    .map(inner -> ((ArrayValue) inner).getValues().stream()
                            .filter(StringValue.class::isInstance)
                            .map(sv -> ((StringValue) sv).getValue())
                            .toList())
                    .toList();
        }

        if (valueState.isExternal() && valueState.getValue() instanceof List<?> outer) {
            return outer.stream()
                    .filter(List.class::isInstance)
                    .map(inner -> ((List<?>) inner).stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .toList())
                    .toList();
        }

        return List.of();
    }

    /**
     * Enforces the scope matrix. Outer array = OR, inner array = AND.
     * Throws {@link AccessDeniedException} if no OR group is fully satisfied.
     */
    private void enforceScopes(Authentication authentication, List<List<String>> scopes) {
        for (List<String> andGroup : scopes) {
            if (andGroup.stream().allMatch(scope -> checkScope(authentication, scope))) {
                return;
            }
        }
        throw new AccessDeniedException("Access denied: insufficient scopes");
    }

    private boolean checkScope(Authentication authentication, String scope) {
        return strategies.stream().anyMatch(s -> s.check(authentication, scope));
    }
}
