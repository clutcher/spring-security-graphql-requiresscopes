# spring-security-graphql-requiresscopes

A Spring Boot library that enforces `@requiresScopes` directives declared in a GraphQL schema at field-execution time — no per-controller annotations required.

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

## Configuration

All configuration lives in `application.yml`. No beans need to be defined in the consuming application.

```yaml
spring:
  security:
    graphql:
      requiresscopes:
        role-authority-prefix: ROLE_   # authority prefix for "role:" scopes (see Strategy 2 below)
        scope-mappings:
          feature: FEATURE_            # "feature:PRICING" → checks for authority "FEATURE_PRICING"
          roles: ROLE_                 # "roles:ADMIN"     → checks for authority "ROLE_ADMIN"
```

The `scope-mappings` key is the scope type name **without** the trailing colon — the library appends it automatically.

---

## How it works

### 1. Request arrives — JWT is converted to Spring Security authorities

Before the library does anything, Spring Security's `JwtAuthenticationConverter` (configured by the application) processes the incoming JWT and populates `Authentication.getAuthorities()` with `GrantedAuthority` objects. For example:

```
JWT claim  "roles": ["ADMIN", "USER"]         →  authorities: ROLE_ADMIN, ROLE_USER
JWT claim  "enabledFeatures": ["PRICING"]     →  authorities: FEATURE_PRICING
```

The library never reads the JWT token directly — it only inspects the already-populated `Authentication` object.

### 2. GraphQL field is resolved — instrumentation intercepts

`RequiresScopesInstrumentation` wraps every field's `DataFetcher`. Before the actual fetch executes, it checks whether the field definition carries a `@requiresScopes` directive.

- **No directive** → passes through immediately, no check performed.
- **Directive present** → extracts the `scopes` argument and runs the enforcement logic.

The `scopes` argument is parsed from the GraphQL AST (SDL literal) or from an already-coerced external value — both representations are handled.

### 3. Scope matrix is evaluated — OR of AND-groups

```
@requiresScopes(scopes: [["feature:PRICING", "role:ADMIN"], ["role:SUPERUSER"]])
```

```
outer array  [  group-A,                          group-B        ]  → OR
                ["feature:PRICING", "role:ADMIN"]  ["role:SUPERUSER"]
inner array      AND                               AND
```

The instrumentation iterates the outer array. As soon as one AND-group passes completely, access is granted and the actual `DataFetcher` is called. If no group passes, `AccessDeniedException` is thrown.

### 4. Each scope is checked — strategies are tried in order

For every scope string (e.g. `"feature:PRICING"`), the instrumentation calls `strategies.stream().anyMatch(s -> s.check(authentication, scope))`. A scope passes if **any** strategy returns `true`.

Three strategies are auto-configured:

---

#### Strategy 1 — `SimpleAuthorityMatchStrategy`

Checks whether the scope value appears **verbatim** in `Authentication.getAuthorities()` (case-insensitive).

```
scope "feature:PRICING"
  → getAuthorities() contains "feature:PRICING" ?
```

Useful when `JwtAuthenticationConverter` is configured to produce authorities that already contain the full scope string including the type prefix.

---

#### Strategy 2 — `RolePrefixAuthorityMatchStrategy`

Handles `role:` scopes specifically. Strips the `"role:"` prefix and prepends the configured role authority prefix, then checks authorities.

```
scope "role:ADMIN"
  → strip "role:"  →  "ADMIN"
  → prepend role authority prefix  →  "ROLE_ADMIN"
  → getAuthorities() contains "ROLE_ADMIN" ?
```

**Role authority prefix resolution order:**

1. `GrantedAuthorityDefaults` bean — if the application has defined one
2. `spring.security.graphql.requiresscopes.role-authority-prefix` property — explicit configuration
3. Default: `ROLE_`

---

#### Strategy 3 — `ClaimPrefixMappingStrategy`

Uses the `requiresscopes.scope-mappings` property map to transform any scope type prefix into its corresponding authority prefix, then checks authorities.

```
scope "feature:PRICING"
  → scope-mappings: { "feature:" → "FEATURE_" }
  → strip "feature:"  →  "PRICING"
  → prepend "FEATURE_"  →  "FEATURE_PRICING"
  → getAuthorities() contains "FEATURE_PRICING" ?
```

Prefix matching iterates the map in declaration order — the first matching entry wins.
If no entry matches the scope prefix, the strategy returns `false`.

---

### 5. Access decision

| Outcome | Result |
|---|---|
| At least one AND-group has all scopes passing | `DataFetcher` executes normally |
| No AND-group fully passes | `AccessDeniedException("Access denied: insufficient scopes")` |

Spring's exception handling translates `AccessDeniedException` into a GraphQL error response — no extra wiring needed.

---

## Customisation

### Add a custom strategy

Implement `ScopeCheckStrategy` and register it as a Spring bean — it is picked up automatically alongside the three defaults:

```java
@Bean
public ScopeCheckStrategy myStrategy() {
    return (authentication, scope) -> /* custom logic */;
}
```

### Replace a default strategy entirely

Each default strategy is annotated with `@ConditionalOnMissingBean`, so providing a bean of the same type disables the default:

```java
@Bean
public ClaimPrefixMappingStrategy claimPrefixMappingStrategy() {
    return new ClaimPrefixMappingStrategy(Map.of("grp:", "GROUP_"));
}
```

### Use `RequiresScopesInstrumentation` without the starter

```java
@Bean
public RequiresScopesInstrumentation requiresScopesInstrumentation(List<ScopeCheckStrategy> strategies) {
    return new RequiresScopesInstrumentation(strategies);
}
```
