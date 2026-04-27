package dev.clutcher.security.graphql.instrumentation;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticatedInstrumentationTest {

    private static final String DIRECTIVE_SDL = """
            directive @authenticated on FIELD_DEFINITION
            """;

    @Test
    void shouldResolveFieldForAuthenticatedUser() {
        GraphQL graphQL = buildGraphQL("""
                type Query {
                  profile: String @authenticated
                }
                """, "profile", env -> "profile-value");
        Authentication authenticated = new TestingAuthenticationToken("user", null, "ROLE_USER");

        ExecutionResult result = execute(graphQL, "{ profile }", authenticated);

        assertTrue(result.getErrors().isEmpty());
        assertEquals("profile-value", dataField(result, "profile"));
    }

    @Test
    void shouldDenyAccessForAnonymousUser() {
        GraphQL graphQL = buildGraphQL("""
                type Query {
                  profile: String @authenticated
                }
                """, "profile", env -> "profile-value");
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        ExecutionResult result = execute(graphQL, "{ profile }", anonymous);

        assertAccessDenied(result);
        assertNull(dataField(result, "profile"));
    }

    @Test
    void shouldDenyAccessWhenNoSecurityContextInGraphQLContext() {
        GraphQL graphQL = buildGraphQL("""
                type Query {
                  profile: String @authenticated
                }
                """, "profile", env -> "profile-value");

        ExecutionInput input = ExecutionInput.newExecutionInput().query("{ profile }").build();
        ExecutionResult result = graphQL.execute(input);

        assertAccessDenied(result);
    }

    @Test
    void shouldPassThroughFieldWithoutDirective() {
        GraphQL graphQL = buildGraphQL("""
                type Query {
                  open: String
                }
                """, "open", env -> "open-value");
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        ExecutionResult result = execute(graphQL, "{ open }", anonymous);

        assertTrue(result.getErrors().isEmpty());
        assertEquals("open-value", dataField(result, "open"));
    }

    private GraphQL buildGraphQL(String querySdl, String fieldName, graphql.schema.DataFetcher<?> fetcher) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(DIRECTIVE_SDL + querySdl);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher(fieldName, fetcher))
                .build();
        return GraphQL.newGraphQL(new SchemaGenerator().makeExecutableSchema(registry, wiring))
                .instrumentation(new AuthenticatedInstrumentation())
                .build();
    }

    private ExecutionResult execute(GraphQL graphQL, String query, Authentication authentication) {
        SecurityContext ctx = new SecurityContextImpl(authentication);
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query(query)
                .graphQLContext(b -> b.put(SecurityContext.class.getName(), ctx))
                .build();
        return graphQL.execute(input);
    }

    private Object dataField(ExecutionResult result, String field) {
        Map<String, Object> data = result.getData();
        return data == null ? null : data.get(field);
    }

    private void assertAccessDenied(ExecutionResult result) {
        assertFalse(result.getErrors().isEmpty(), "expected at least one GraphQL error");
        String messages = result.getErrors().stream()
                .map(Object::toString)
                .reduce("", (a, b) -> a + " | " + b);
        assertTrue(messages.toLowerCase().contains("access denied"),
                "expected 'access denied' in errors: " + messages);
    }
}
