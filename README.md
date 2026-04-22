# Spring Security GraphQL RequiresScopes

A Spring Boot library that enforces `@requiresScopes` directives declared in a GraphQL schema at field-execution time — no per-controller annotations required.

---

## Table of Contents

1. [Overview](#overview)
2. [Project Structure](#project-structure)
3. [Getting Started](#getting-started)
    - [Installation](#installation)
    - [Basic Usage](#basic-usage)
4. [How It Works](#how-it-works)
    - [Scope Evaluation](#scope-evaluation)
    - [Built-in Strategies](#built-in-strategies)
5. [Customisation](#customisation)
6. [Implementation Details](#implementation-details)

---

## Overview

By default, securing individual GraphQL fields in Spring Boot requires either custom `DataFetcher` logic or manual checks scattered across resolvers. There is no built-in mechanism to enforce access control rules declared directly in the GraphQL schema.

**Spring Security GraphQL RequiresScopes** addresses this by hooking into graphql-java's instrumentation pipeline. When a field carries an `@requiresScopes` directive, the library intercepts the fetch and checks the current user's Spring Security authorities — before any resolver code runs.

### Key Features

- Schema-driven access control via the `@requiresScopes` GraphQL directive
- OR-of-AND scope evaluation semantics (Apollo Federation compatible)
- Three built-in scope check strategies covering common authority conventions
- Extensible `ScopeCheckStrategy` SPI for custom authority matching logic
- Spring Boot auto-configuration with sensible defaults
- Zero overhead on fields without `@requiresScopes`

---

## Project Structure

This project consists of two libraries:

- **`spring-security-graphql-requiresscopes`** — Core library: `ScopeCheckStrategy` interface, built-in strategy implementations, and `RequiresScopesInstrumentation`
- **`spring-security-graphql-requiresscopes-starter`** — Spring Boot starter: auto-configures the default strategies and registers the instrumentation

---

## Getting Started

### Installation

Add the starter dependency to your Spring Boot project:

#### Gradle

```kotlin
implementation("dev.clutcher.security:spring-security-graphql-requiresscopes-starter:1.0.0")
```

#### Maven

```xml
<dependency>
    <groupId>dev.clutcher.security</groupId>
    <artifactId>spring-security-graphql-requiresscopes-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Basic Usage

#### Schema Setup

Declare the directive in your GraphQL schema and apply it to fields:

```graphql
directive @requiresScopes(scopes: [[String!]!]!) on FIELD_DEFINITION

type Query {
    pricing: PricingData @requiresScopes(scopes: [["feature:PRICING"]])
    adminReport: Report  @requiresScopes(scopes: [["role:ADMIN"]])
    dashboard: Dashboard @requiresScopes(scopes: [["feature:PRICING", "role:ADMIN"], ["role:SUPERUSER"]])
}
```

#### Configuration

All configuration lives under `spring.security.graphql.requiresscopes` in `application.yml`. No beans need to be defined in the consuming application.

```yaml
spring:
  security:
    graphql:
      requiresscopes:
        role-authority-prefix: ROLE_   # authority prefix for "role:" scopes (default: ROLE_)
        scope-mappings:
          feature: FEATURE_            # "feature:PRICING" → checks for authority "FEATURE_PRICING"
          roles: ROLE_                 # "roles:ADMIN"     → checks for authority "ROLE_ADMIN"
```

The `scope-mappings` key is the scope type name **without** the trailing colon — the library appends it automatically.

---

## How It Works

### Scope Evaluation

The `@requiresScopes` directive accepts a two-dimensional scope matrix with OR-of-AND semantics:

- **Outer array = OR** — at least one inner group must pass
- **Inner array = AND** — every scope in a group must be satisfied

```
@requiresScopes(scopes: [["feature:PRICING", "role:ADMIN"], ["role:SUPERUSER"]])
```

```
outer array  [  group-A,                           group-B         ]  → OR
                ["feature:PRICING", "role:ADMIN"]   ["role:SUPERUSER"]
inner array      AND                                AND
```

The `dashboard` example above means: _(has PRICING feature AND is ADMIN)_ **OR** _(is SUPERUSER)_.

| Outcome | Result |
|---|---|
| At least one AND-group has all scopes passing | `DataFetcher` executes normally |
| No AND-group fully passes | `AccessDeniedException("Access denied: insufficient scopes")` |

Spring's exception handling translates `AccessDeniedException` into a GraphQL error response — no extra wiring needed.

### Built-in Strategies

For every scope string the instrumentation calls each registered `ScopeCheckStrategy` in order, short-circuiting on the first `true`.

Three strategies are auto-configured:

#### Strategy 1 — `SimpleAuthorityMatchStrategy`

Checks whether the scope value appears **verbatim** in `Authentication.getAuthorities()` (case-insensitive):

```
scope "feature:PRICING"
  → getAuthorities() contains "feature:PRICING" ?
```

Useful when the `JwtAuthenticationConverter` is configured to produce authorities that already contain the full scope string including the type prefix.

#### Strategy 2 — `RolePrefixAuthorityMatchStrategy`

Handles `role:` scopes specifically. Strips the `"role:"` prefix and prepends the configured role authority prefix:

```
scope "role:ADMIN"
  → strip "role:"  →  "ADMIN"
  → prepend role authority prefix  →  "ROLE_ADMIN"
  → getAuthorities() contains "ROLE_ADMIN" ?
```

**Role authority prefix resolution order:**

1. `GrantedAuthorityDefaults` bean — if the application has defined one
2. `spring.security.graphql.requiresscopes.role-authority-prefix` property
3. Default: `ROLE_`

#### Strategy 3 — `ClaimPrefixMappingStrategy`

Uses the `scope-mappings` property to transform any scope type prefix into its corresponding authority prefix:

```
scope "feature:PRICING"
  → scope-mappings: { "feature:" → "FEATURE_" }
  → strip "feature:"  →  "PRICING"
  → prepend "FEATURE_"  →  "FEATURE_PRICING"
  → getAuthorities() contains "FEATURE_PRICING" ?
```

Prefix matching iterates the map in declaration order — the first matching entry wins. If no entry matches the scope prefix, the strategy returns `false`.

---

## Customisation

### Add a Custom Strategy

Implement `ScopeCheckStrategy` and register it as a Spring bean — it is picked up automatically alongside the three defaults:

```java
@Bean
public ScopeCheckStrategy myStrategy() {
    return (authentication, scope) -> /* custom logic */;
}
```

### Replace a Default Strategy

Each default strategy is guarded by `@ConditionalOnMissingBean` on its concrete class, so providing a bean of the same type disables the default without affecting the others:

```java
@Bean
public ClaimPrefixMappingStrategy claimPrefixMappingStrategy() {
    return new ClaimPrefixMappingStrategy(Map.of("grp:", "GROUP_"));
}
```

### Use Without the Starter

```java
@Bean
public RequiresScopesInstrumentation requiresScopesInstrumentation(List<ScopeCheckStrategy> strategies) {
    return new RequiresScopesInstrumentation(strategies);
}
```

---

## Implementation Details

### spring-security-graphql-requiresscopes

- **`RequiresScopesInstrumentation`** — Extends `SimplePerformantInstrumentation`. Wraps every field's `DataFetcher` via `instrumentDataFetcher`. Fields without `@requiresScopes` are returned unchanged (zero overhead). The `scopes` argument is extracted once outside the returned lambda, so the AST walk happens per-field-definition, not per-fetch.
- **`ScopeCheckStrategy`** — SPI interface: `check(Authentication, String) -> boolean`. Strategies are tried in the order Spring injects them and short-circuit on first `true`.
- **`SimpleAuthorityMatchStrategy`** — Verbatim case-insensitive authority match.
- **`RolePrefixAuthorityMatchStrategy`** — Strips the `"role:"` prefix and prepends the configured role authority prefix.
- **`ClaimPrefixMappingStrategy`** — Map-driven prefix transformation built from the `scope-mappings` property.

### spring-security-graphql-requiresscopes-starter

`SchemaSecurityAutoConfiguration` creates the following beans (each guarded by `@ConditionalOnMissingBean` on its concrete class):

1. **`SimpleAuthorityMatchStrategy`** — Exact authority match, no configuration required.
2. **`RolePrefixAuthorityMatchStrategy`** — Resolves the role prefix from `GrantedAuthorityDefaults` bean, the `role-authority-prefix` property, or the default `ROLE_`.
3. **`ClaimPrefixMappingStrategy`** — Built from the `scope-mappings` property. The same map also drives the auto-configured `JwtAuthenticationConverter`.
4. **`JwtAuthenticationConverter`** — One `JwtGrantedAuthoritiesConverter` per `scope-mappings` entry, using the map key as the JWT claim name and the map value as the authority prefix. Only registered when no `JwtAuthenticationConverter` bean is present.
