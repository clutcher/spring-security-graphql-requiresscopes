# spring-security-graphql-requiresscopes

A Spring Boot library that enforces `@requiresScopes` directives declared in a GraphQL schema at field-execution time — no per-controller annotations required.

Scope checking is delegated to a configurable list of `ScopeCheckStrategy` beans, making it easy to extend or replace the default behaviour without touching library code.

## Project Structure

- **`spring-security-graphql-requiresscopes`** — Core library: `ScopeCheckStrategy` interface, built-in strategy implementations, and `RequiresScopesInstrumentation`
- **`spring-security-graphql-requiresscopes-starter`** — Spring Boot starter: auto-configures the default strategies and registers the instrumentation

## Installation

```kotlin
implementation("dev.clutcher.security:spring-security-graphql-requiresscopes-starter:1.0.0")
```

## Schema setup

Declare the directive in your GraphQL schema and apply it to fields:

```graphql
directive @requiresScopes(scopes: [[String!]!]!) on FIELD_DEFINITION

type Query {
    pricing: PricingData @requiresScopes(scopes: [["feature:PRICING"]])
    adminReport: Report  @requiresScopes(scopes: [["role:ADMIN"]])
    dashboard: Dashboard @requiresScopes(scopes: [["feature:PRICING", "role:ADMIN"], ["role:SUPERUSER"]])
}
```

### Scope semantics (Apollo Federation)

- **Outer array = OR** — at least one inner group must pass
- **Inner array = AND** — every scope in a group must be satisfied

The third example above means: _(has PRICING feature AND is ADMIN)_ **OR** _(is SUPERUSER)_.

## How scope checking works

A field is accessible when at least one registered `ScopeCheckStrategy` returns `true` for every scope in a passing AND-group.

Three strategies are auto-configured by default:

### Strategy 1 — `SimpleAuthorityMatchStrategy`

Checks whether the scope value exists **as-is** in `authentication.getAuthorities()` (case-insensitive).

```
scope "feature:PRICING"  →  looks for authority "feature:PRICING"
```

Useful when the application's `JwtGrantedAuthoritiesConverter` is configured to produce authorities that already include the scope-type prefix.

### Strategy 2 — `RolePrefixAuthorityMatchStrategy`

Strips the `role:` prefix, prepends the Spring Security role prefix (read from `GrantedAuthorityDefaults`, defaults to `ROLE_`), and checks authorities.

```
scope "role:ADMIN"  →  looks for authority "ROLE_ADMIN"
```

The role prefix stays in sync with the application's `GrantedAuthorityDefaults` bean automatically.

### Strategy 3 — `ClaimPrefixMappingStrategy`

Uses a `Map<String, String>` that maps scope-type prefixes to Spring Security authority prefixes. The map is built at startup by inspecting the `JwtAuthenticationConverter` bean (via `JwtGrantedAuthoritiesConverter`) and falls back to `{"feature:" → "FEATURE_", "role:" → "ROLE_"}` when the bean is absent or its settings cannot be read.

```
scope "feature:PRICING"  →  strips "feature:"  →  prepends "FEATURE_"  →  looks for "FEATURE_PRICING"
scope "role:ADMIN"       →  strips "role:"      →  prepends "ROLE_"      →  looks for "ROLE_ADMIN"
```

## Customisation

### Override the prefix mapping (Strategy 3)

Provide a `ClaimPrefixMappingStrategy` bean with the exact mappings your JWT converter produces:

```java
@Bean
public ClaimPrefixMappingStrategy claimPrefixMappingStrategy() {
    return new ClaimPrefixMappingStrategy(Map.of(
        "feature:", "FEATURE_",
        "role:",    "ROLE_",
        "grp:",     "GROUP_"
    ));
}
```

### Override the role prefix (Strategy 2)

Provide a `GrantedAuthorityDefaults` bean with a custom prefix:

```java
@Bean
public GrantedAuthorityDefaults grantedAuthorityDefaults() {
    return new GrantedAuthorityDefaults("CUSTOM_ROLE_");
}
```

### Add a custom strategy

Implement `ScopeCheckStrategy` and register it as a Spring bean — it is picked up automatically:

```java
@Bean
public ScopeCheckStrategy myStrategy() {
    return (authentication, scope) -> /* custom logic */;
}
```

### Disable a default strategy

Declare a bean of the same type annotated with `@Primary`, or exclude the auto-configuration class and wire everything manually.

### Use `RequiresScopesInstrumentation` without the starter

```java
@Bean
public RequiresScopesInstrumentation requiresScopesInstrumentation(List<ScopeCheckStrategy> strategies) {
    return new RequiresScopesInstrumentation(strategies);
}
```
