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
- Two built-in scope check strategies covering common authority conventions
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
        scope-mappings:
          feature: FEATURE_            # "feature:PRICING" → checks for authority "FEATURE_PRICING"
```

The `scope-mappings` key is the scope type name **without** the trailing colon — the library appends it automatically.

A `"role:"` entry is **auto-registered** by the starter using the prefix from Spring Security's `GrantedAuthorityDefaults` bean (or the default `"ROLE_"` if none is declared). To customise the role prefix, declare a `GrantedAuthorityDefaults` bean:

```java
@Bean
public GrantedAuthorityDefaults grantedAuthorityDefaults() {
    return new GrantedAuthorityDefaults("ACCESS_");
}
```

A user-supplied `role:` entry in `scope-mappings` overrides the auto-registered one.

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

Two strategies are auto-configured:

#### Strategy 1 — `SimpleAuthorityMatchStrategy`

Checks whether the scope value appears **verbatim** in `Authentication.getAuthorities()`:

```
scope "feature:PRICING"
  → getAuthorities() contains "feature:PRICING" ?
```

Useful when the `JwtAuthenticationConverter` is configured to produce authorities that already contain the full scope string including the type prefix.

#### Strategy 2 — `ClaimPrefixMappingStrategy`

Transforms any scope type prefix into its corresponding authority prefix and checks the result against `Authentication.getAuthorities()`. The prefix map combines an auto-registered `"role:"` entry with every entry from the `scope-mappings` property:

```
scope "feature:PRICING"
  → prefix map: { "role:" → "ROLE_", "feature:" → "FEATURE_" }
  → strip "feature:"  →  "PRICING"
  → prepend "FEATURE_"  →  "FEATURE_PRICING"
  → getAuthorities() contains "FEATURE_PRICING" ?
```

Prefix matching iterates the map in declaration order — the first matching entry wins. If no entry matches the scope prefix, the strategy returns `false`.

**Role authority prefix resolution** (used for the auto-registered `"role:"` entry):

1. `GrantedAuthorityDefaults` bean — if the application has defined one
2. Spring Security's default: `"ROLE_"`

A user-supplied `role:` entry in `scope-mappings` overrides the auto-registered one (the autoconfig inserts the auto-registered entry first, then overlays `scope-mappings`).

---

## Customisation

### Add a Custom Strategy

Implement `ScopeCheckStrategy` and register it as a Spring bean — it is picked up automatically alongside the two defaults:

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
- **`SimpleAuthorityMatchStrategy`** — Verbatim authority match.
- **`ClaimPrefixMappingStrategy`** — Map-driven prefix transformation built from the auto-registered `"role:"` entry plus the `scope-mappings` property.

### spring-security-graphql-requiresscopes-starter

`SchemaSecurityAutoConfiguration` creates the following beans (each guarded by `@ConditionalOnMissingBean` on its concrete class):

1. **`SimpleAuthorityMatchStrategy`** — Exact authority match, no configuration required.
2. **`ClaimPrefixMappingStrategy`** — Built from a combined `LinkedHashMap` containing the auto-registered `"role:"` entry (prefix resolved from the `GrantedAuthorityDefaults` bean, or Spring Security's default `"ROLE_"` if none is declared) followed by every `scope-mappings` entry. A user-supplied `role:` entry in `scope-mappings` overrides the auto-registered one. The `scope-mappings` portion also drives the auto-configured `JwtAuthenticationConverter`.
3. **`JwtAuthenticationConverter`** — One `JwtGrantedAuthoritiesConverter` per `scope-mappings` entry, using the map key as the JWT claim name and the map value as the authority prefix. Only registered when no `JwtAuthenticationConverter` bean is present.
