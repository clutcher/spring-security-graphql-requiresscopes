package dev.clutcher.security.graphql.instrumentation;

import dev.clutcher.security.graphql.strategy.ClaimPrefixMappingStrategy;
import dev.clutcher.security.graphql.strategy.ScopeCheckStrategy;
import dev.clutcher.security.graphql.strategy.SimpleAuthorityMatchStrategy;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiresScopesInstrumentationTest {

    private static final String DIRECTIVE_SDL = """
            directive @requiresScopes(scopes: [[String!]!]!) on FIELD_DEFINITION
            """;

    private final List<ScopeCheckStrategy> strategies = List.of(
            new SimpleAuthorityMatchStrategy(),
            new ClaimPrefixMappingStrategy(Map.of("role:", "ROLE_", "feature:", "FEATURE_"))
    );

    @Test
    void shouldResolveFieldWhenNoDirectiveIsPresent() {
        // Given
        GraphQL graphQL = buildGraphQlFromSdl("""
                type Query {
                  open: String
                }
                """, Map.of("open", env -> "open-value"));
        Authentication anyAuthentication = authWith();

        // When
        ExecutionResult result = execute(graphQL, "{ open }", anyAuthentication);

        // Then
        assertTrue(result.getErrors().isEmpty());
        assertEquals("open-value", dataField(result, "open"));
    }

    @Test
    void shouldResolveFieldWhenSingleAndGroupIsSatisfied() {
        // Given
        GraphQL graphQL = buildGraphQlFromSdl("""
                type Query {
                  pricing: String @requiresScopes(scopes: [["feature:PRICING"]])
                }
                """, Map.of("pricing", env -> "pricing-value"));
        Authentication authenticated = authWith("FEATURE_PRICING");

        // When
        ExecutionResult result = execute(graphQL, "{ pricing }", authenticated);

        // Then
        assertTrue(result.getErrors().isEmpty());
        assertEquals("pricing-value", dataField(result, "pricing"));
    }

    @Test
    void shouldDenyAccessWhenSingleAndGroupScopeIsMissing() {
        // Given
        GraphQL graphQL = buildGraphQlFromSdl("""
                type Query {
                  pricing: String @requiresScopes(scopes: [["feature:PRICING"]])
                }
                """, Map.of("pricing", env -> "pricing-value"));
        Authentication authenticated = authWith("FEATURE_CART");

        // When
        ExecutionResult result = execute(graphQL, "{ pricing }", authenticated);

        // Then
        assertAccessDenied(result);
        assertNull(dataField(result, "pricing"));
    }

    @Test
    void shouldDenyAccessWhenOnlyOneScopeOfAndGroupIsSatisfied() {
        // Given
        GraphQL graphQL = buildGraphQlFromSdl("""
                type Query {
                  adminPricing: String @requiresScopes(scopes: [["feature:PRICING", "role:ADMIN"]])
                }
                """, Map.of("adminPricing", env -> "admin-pricing-value"));
        Authentication authenticated = authWith("FEATURE_PRICING");

        // When
        ExecutionResult result = execute(graphQL, "{ adminPricing }", authenticated);

        // Then
        assertAccessDenied(result);
    }

    @Test
    void shouldResolveFieldWhenAllScopesOfAndGroupAreSatisfied() {
        // Given
        GraphQL graphQL = buildGraphQlFromSdl("""
                type Query {
                  adminPricing: String @requiresScopes(scopes: [["feature:PRICING", "role:ADMIN"]])
                }
                """, Map.of("adminPricing", env -> "admin-pricing-value"));
        Authentication authenticated = authWith("FEATURE_PRICING", "ROLE_ADMIN");

        // When
        ExecutionResult result = execute(graphQL, "{ adminPricing }", authenticated);

        // Then
        assertTrue(result.getErrors().isEmpty());
        assertEquals("admin-pricing-value", dataField(result, "adminPricing"));
    }

    @Test
    void shouldResolveFieldWhenAnyOrGroupPasses() {
        // Given
        GraphQL graphQL = buildGraphQlFromSdl("""
                type Query {
                  dashboard: String @requiresScopes(scopes: [["feature:PRICING", "role:ADMIN"], ["role:SUPERUSER"]])
                }
                """, Map.of("dashboard", env -> "dashboard-value"));
        Authentication authenticated = authWith("ROLE_SUPERUSER");

        // When
        ExecutionResult result = execute(graphQL, "{ dashboard }", authenticated);

        // Then
        assertTrue(result.getErrors().isEmpty());
        assertEquals("dashboard-value", dataField(result, "dashboard"));
    }

    @Test
    void shouldDenyAccessWhenNoOrGroupFullyPasses() {
        // Given
        GraphQL graphQL = buildGraphQlFromSdl("""
                type Query {
                  dashboard: String @requiresScopes(scopes: [["feature:PRICING", "role:ADMIN"], ["role:SUPERUSER"]])
                }
                """, Map.of("dashboard", env -> "dashboard-value"));
        Authentication authenticated = authWith("FEATURE_PRICING", "ROLE_USER");

        // When
        ExecutionResult result = execute(graphQL, "{ dashboard }", authenticated);

        // Then
        assertAccessDenied(result);
    }

    @Test
    void shouldDenyAccessWhenNoAuthenticationIsInContext() {
        // Given
        GraphQL graphQL = buildGraphQlFromSdl("""
                type Query {
                  pricing: String @requiresScopes(scopes: [["feature:PRICING"]])
                }
                """, Map.of("pricing", env -> "pricing-value"));

        // When
        ExecutionResult result = executeWithoutSecurityContext(graphQL, "{ pricing }");

        // Then
        assertAccessDenied(result);
    }

    @Test
    void shouldDenyAccessWhenScopesOuterArrayIsEmpty() {
        // Given
        GraphQL graphQL = buildGraphQlFromSdl("""
                type Query {
                  unreachable: String @requiresScopes(scopes: [])
                }
                """, Map.of("unreachable", env -> "unreachable-value"));
        Authentication authenticated = authWith("FEATURE_PRICING", "ROLE_ADMIN", "ROLE_SUPERUSER");

        // When
        ExecutionResult result = execute(graphQL, "{ unreachable }", authenticated);

        // Then
        assertAccessDenied(result);
    }

    @Test
    void shouldEvaluateExternalValueFormIdenticallyToLiteralForm() {
        // Given
        GraphQL graphQL = buildGraphQlWithProgrammaticDirective(List.of(List.of("feature:PRICING")));
        Authentication authenticated = authWith("FEATURE_PRICING");

        // When
        ExecutionResult result = execute(graphQL, "{ protectedField }", authenticated);

        // Then
        assertTrue(result.getErrors().isEmpty());
        assertEquals("protected-value", dataField(result, "protectedField"));
    }

    private Authentication authWith(String... authorities) {
        return new TestingAuthenticationToken("user", null, authorities);
    }

    private GraphQL buildGraphQlFromSdl(String querySdl, Map<String, DataFetcher<?>> fetchersByField) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(DIRECTIVE_SDL + querySdl);
        RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();
        wiringBuilder.type("Query", queryBuilder -> {
            fetchersByField.forEach(queryBuilder::dataFetcher);
            return queryBuilder;
        });
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiringBuilder.build());
        return GraphQL.newGraphQL(schema)
                .instrumentation(new RequiresScopesInstrumentation(strategies))
                .build();
    }

    private GraphQL buildGraphQlWithProgrammaticDirective(List<List<String>> scopes) {
        GraphQLAppliedDirective scopesDirective = GraphQLAppliedDirective.newDirective()
                .name("requiresScopes")
                .argument(GraphQLAppliedDirectiveArgument.newArgument()
                        .name("scopes")
                        .type(GraphQLList.list(GraphQLList.list(GraphQLNonNull.nonNull(Scalars.GraphQLString))))
                        .valueProgrammatic(scopes)
                        .build())
                .build();

        GraphQLFieldDefinition protectedField = GraphQLFieldDefinition.newFieldDefinition()
                .name("protectedField")
                .type(Scalars.GraphQLString)
                .withAppliedDirective(scopesDirective)
                .build();

        GraphQLObjectType queryType = GraphQLObjectType.newObject()
                .name("Query")
                .field(protectedField)
                .build();

        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(
                        FieldCoordinates.coordinates("Query", "protectedField"),
                        (DataFetcher<String>) env -> "protected-value")
                .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .codeRegistry(codeRegistry)
                .build();

        return GraphQL.newGraphQL(schema)
                .instrumentation(new RequiresScopesInstrumentation(strategies))
                .build();
    }

    private ExecutionResult execute(GraphQL graphQL, String query, Authentication authentication) {
        SecurityContext securityContext = new SecurityContextImpl(authentication);
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query(query)
                .graphQLContext(contextBuilder -> contextBuilder.put(SecurityContext.class.getName(), securityContext))
                .build();
        return graphQL.execute(input);
    }

    private ExecutionResult executeWithoutSecurityContext(GraphQL graphQL, String query) {
        ExecutionInput input = ExecutionInput.newExecutionInput().query(query).build();
        return graphQL.execute(input);
    }

    private Object dataField(ExecutionResult result, String fieldName) {
        Map<String, Object> data = result.getData();
        return data == null ? null : data.get(fieldName);
    }

    private void assertAccessDenied(ExecutionResult result) {
        assertFalse(result.getErrors().isEmpty(), "expected at least one GraphQL error");
        String combinedMessages = result.getErrors().stream()
                .map(Object::toString)
                .reduce("", (a, b) -> a + " | " + b);
        assertTrue(combinedMessages.toLowerCase().contains("access denied"),
                "expected 'access denied' in errors: " + combinedMessages);
    }
}
