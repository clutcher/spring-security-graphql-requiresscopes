# Spring Security GraphQL RequiresScopes

A Spring Boot library that enforces `@requiresScopes` and `@authenticated` directives declared in a GraphQL schema at field-execution time — no per-controller annotations required.

---

## Table of Contents

1. [Overview](#overview)
2. [Project Structure](#project-structure)
3. [Getting Started](#getting-started)
    - [Installation](#installation)
    - [Basic Usage](#basic-usage)
4. [Configuration](#configuration)
    - [Prerequisites](#prerequisites)
    - [Zero-config behaviour](#zero-config-behaviour)
    - [Relation to Spring Boot's built-in JWT properties](#relation-to-spring-boots-built-in-jwt-properties)
    - [Scope mappings (additive layer)](#scope-mappings-additive-layer)
    - [Customising the role authority prefix](#customising-the-role-authority-prefix)
5. [How It Works](#how-it-works)
    - [Scope Evaluation](#scope-evaluation)
    - [Built-in Strategies](#built-in-strategies)
    - [Authenticated Directive](#authenticated-directive)
6. [Cross-compatibility with Apollo Router / Hive Gateway](#cross-compatibility-with-apollo-router--hive-gateway)
7. [End-to-End Example](#end-to-end-example)
8. [Customisation](#customisation)
9. [Troubleshooting](#troubleshooting)
10. [Implementation Details](#implementation-details)
11. [Specification References](#specification-references)

---

## Overview

This library adds support for the `@requiresScopes` and `@authenticated` GraphQL directives — defined by [Apollo Federation](https://www.apollographql.com/docs/graphos/reference/federation/directives) and used by Apollo Router and Hive Gateway — to Spring GraphQL applications. The directive semantics (OR-of-AND scope matrix, case-sensitive comparison) are preserved; scopes are resolved against Spring Security's `Authentication.getAuthorities()` rather than an OAuth2 `scope` claim in the JWT. `@authenticated` denies access when the caller is anonymous or unauthenticated.

With default Spring Security, Apollo-shaped schemas — `@requiresScopes(scopes: [["admin"]])` — just work: Spring Security produces a `SCOPE_admin` authority from the JWT `scope` claim, and the library's new `ScopePrefixAuthorityMatchStrategy` bridges the raw Apollo scope to the prefixed authority without requiring any configuration.

Without this library, securing individual GraphQL fields in Spring Boot requires custom `DataFetcher` logic or manual checks scattered across resolvers. There is no built-in mechanism to enforce access control rules declared directly in the GraphQL schema.

### Key Features

- Schema-driven access control via the Federation `@requiresScopes` and `@authenticated` directives
- OR-of-AND scope evaluation semantics (Apollo Federation compatible)
- Strict case-sensitive scope/authority matching — aligned with RFC 6749, Apollo Router, and Spring Security
- Zero-config interoperability with Apollo-shaped schemas on default Spring Security
- Three built-in scope check strategies covering common authority conventions
- Extensible `ScopeCheckStrategy` SPI for custom authority matching logic
- Spring Boot auto-configuration that respects Spring Security's JWT properties
- Zero overhead on fields without `@requiresScopes` or `@authenticated`

---

## Project Structure

This project consists of two libraries:

- **`spring-security-graphql-requiresscopes`** — Core library: `ScopeCheckStrategy` interface, built-in strategy implementations, the `AuthorityMatcher` helper, `RequiresScopesInstrumentation`, and `AuthenticatedInstrumentation`
- **`spring-security-graphql-requiresscopes-starter`** — Spring Boot starter: auto-configures the default strategies and registers both instrumentations

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

Declare the directives in your GraphQL schema and apply them to fields. Scope values can use the Apollo-compatible shape (raw tokens) or this library's Spring-specific extension (`type:VALUE` prefix convention). `@authenticated` takes no arguments and simply requires a non-anonymous, authenticated caller:

```graphql
directive @requiresScopes(scopes: [[String!]!]!) on FIELD_DEFINITION
directive @authenticated on FIELD_DEFINITION

type Query {
    # Any logged-in user, no scope check.
    profile: Profile @authenticated

    # Apollo-compatible: raw OAuth2 scope tokens. Works against Spring Security's SCOPE_* authorities.
    pricing: PricingData @requiresScopes(scopes: [["admin"]])

    # Spring-specific extension: "type:VALUE" resolves through scope-mappings / role auto-registration.
    adminReport: Report  @requiresScopes(scopes: [["role:ADMIN"]])

    # Mixed: any AND group can use either convention.
    dashboard: Dashboard @requiresScopes(scopes: [["feature:PRICING", "role:ADMIN"], ["admin"]])

    # Directives compose: caller must be authenticated AND have the scope.
    auditLog: AuditLog @authenticated @requiresScopes(scopes: [["role:ADMIN"]])
}
```

> **⚠️ Case sensitivity.** Scope values must case-match the authorities produced by your `JwtGrantedAuthoritiesConverter`. With Spring Security's canonical uppercase prefixes (e.g. `ROLE_ADMIN`, `FEATURE_PRICING`) and default `SCOPE_` prefix on the scope claim, write scope values with matching case: `"role:ADMIN"`, `"admin"`, `"SCOPE_admin"` — **not** `"role:admin"` or `"ADMIN"` when the authority is lowercase. This mirrors Apollo Router's behaviour and RFC 6749 §3.3 (OAuth 2.0 scopes are defined as case-sensitive strings).

---

## Configuration

### Prerequisites

The JWT → authority flow requires Spring Boot OAuth2 resource server on the classpath:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
```

…and JWT decoding configured (issuer URI or public key):

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://your-auth-server/
```

Without this, Spring never decodes the incoming token, no authorities are produced, and every `@requiresScopes`-protected field denies access.

### Zero-config behaviour

With nothing configured beyond the OAuth2 resource server wiring, the library delegates to Spring Security's default JWT converter: the `scope`/`scp` claim is split into values and each is prepended with `SCOPE_`. The library then matches Apollo-shaped scope values against those authorities automatically:

```
JWT { "scope": "admin read write" }  →  authorities: SCOPE_admin, SCOPE_read, SCOPE_write

@requiresScopes(scopes: [["admin"]])       →  matches SCOPE_admin  ✓
@requiresScopes(scopes: [["SCOPE_admin"]]) →  matches verbatim     ✓
```

You can still write `@requiresScopes(scopes: [["role:ADMIN"]])` — the auto-registered `role:` mapping resolves it to `ROLE_ADMIN` — but roles only materialise as authorities if your JWT configuration produces them. See [Scope mappings](#scope-mappings-additive-layer) for how.

### Relation to Spring Boot's built-in JWT properties

Spring Boot exposes three properties for customising the JWT → authority conversion:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          authorities-claim-name: scope     # which JWT claim to read (default fallback: "scope" then "scp")
          authority-prefix: SCOPE_          # prefix prepended to each value (default: "SCOPE_")
          authorities-claim-delimiter: " "  # regex split for space-separated values (default: " ")
```

This library reads those properties and adapts accordingly. The Apollo-compatible `ScopePrefixAuthorityMatchStrategy` uses the configured `authority-prefix`, so if you set `authority-prefix=ACCESS_`, the Apollo shape `@requiresScopes(scopes: [["admin"]])` matches `ACCESS_admin`.

The Apollo-compatible strategy is registered **only** when `authorities-claim-name` is unset, `"scope"`, or `"scp"` — i.e. when Spring Security is still reading OAuth2-scope claims. If you retarget the converter to a different claim (`roles`, `groups`, …), the Apollo shape no longer applies semantically; use the `type:VALUE` convention via `ClaimPrefixMappingStrategy` instead.

### Scope mappings (additive layer)

The library's own `scope-mappings` property adds per-claim converters **on top of** Spring Security's default. Each entry wires both sides of the flow:

```yaml
spring:
  security:
    graphql:
      requiresscopes:
        scope-mappings:
          feature: FEATURE_
          roles: ROLE_
```

The map key (`feature`, `roles`) serves two roles:

1. **JWT claim name.** The starter adds a `JwtGrantedAuthoritiesConverter` per entry: `feature` claim values become `FEATURE_*` authorities, `roles` claim values become `ROLE_*` authorities. The default `scope`/`scp` → `SCOPE_` converter **still runs** alongside — the mappings are additive, not a replacement.
2. **Scope prefix in the schema.** The library appends `:`, so `@requiresScopes(scopes: [["feature:PRICING"]])` is matched against `FEATURE_PRICING` authorities.

The map value is the Spring Security authority prefix.

**End-to-end walkthrough** with `scope-mappings: { feature: FEATURE_, roles: ROLE_ }` and a JWT:

```json
{ "sub": "alice", "scope": "admin", "feature": ["PRICING"], "roles": ["ADMIN"] }
```

| Step | Produces |
|---|---|
| Spring Security default converter reads `scope` | `SCOPE_admin` |
| Our converter for `feature` entry reads `feature` | `FEATURE_PRICING` |
| Our converter for `roles` entry reads `roles` | `ROLE_ADMIN` |
| Final authority set | `[SCOPE_admin, FEATURE_PRICING, ROLE_ADMIN]` |

Any of the three shapes now resolve:

- `@requiresScopes(scopes: [["admin"]])` → `SCOPE_admin` (Apollo shape, via `ScopePrefixAuthorityMatchStrategy`)
- `@requiresScopes(scopes: [["feature:PRICING"]])` → `FEATURE_PRICING` (via `ClaimPrefixMappingStrategy`)
- `@requiresScopes(scopes: [["role:ADMIN"]])` → `ROLE_ADMIN` (auto-registered `role:` entry)

### Customising the role authority prefix

A `"role:"` entry is **auto-registered** in the prefix-mapping strategy using the prefix from Spring Security's `GrantedAuthorityDefaults` bean (or the default `"ROLE_"` if none is declared). To customise, declare a `GrantedAuthorityDefaults` bean:

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

Three strategies are auto-configured:

#### Strategy 1 — `SimpleAuthorityMatchStrategy`

Checks whether the scope value appears **verbatim** in `Authentication.getAuthorities()`. Comparison is a case-sensitive `String.equals` — the same rule used by Spring Security's `AuthoritiesAuthorizationManager` and Apollo Router's reference implementation of `@requiresScopes`.

```
scope "SCOPE_admin"
  → getAuthorities() contains "SCOPE_admin" ?
```

Useful when the schema author writes the full canonical authority string (e.g. `"SCOPE_admin"`, `"ROLE_ADMIN"`).

#### Strategy 2 — `ScopePrefixAuthorityMatchStrategy`

Bridges Apollo-style raw scope values to Spring Security's prefixed authority model. Prepends the configured `authority-prefix` (default `"SCOPE_"`, or whatever `spring.security.oauth2.resourceserver.jwt.authority-prefix` is set to) to the scope and checks the result against `Authentication.getAuthorities()`.

```
scope "admin" (with default prefix)
  → prepend "SCOPE_"  →  "SCOPE_admin"
  → getAuthorities() contains "SCOPE_admin" ?
```

**Registered automatically only when Spring Security is reading the OAuth2 `scope` claim** — that is, when `spring.security.oauth2.resourceserver.jwt.authorities-claim-name` is unset, `"scope"`, or `"scp"`. If the user retargets the converter to a different claim (`roles`, `groups`, …), this strategy is not registered because the Apollo-shape semantic no longer applies.

#### Strategy 3 — `ClaimPrefixMappingStrategy`

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

### Authenticated Directive

`@authenticated` is a parameterless field directive that gates access on whether the caller is authenticated at all — independent of any scopes or authorities. Declare it in the schema:

```graphql
directive @authenticated on FIELD_DEFINITION

type Query {
    profile: Profile @authenticated
}
```

The library's `AuthenticatedInstrumentation` wraps every field's `DataFetcher`. Fields without `@authenticated` are returned unchanged (zero overhead). On a field that carries the directive, the wrapping fetcher resolves `Authentication` from the GraphQL context and denies access when **any** of the following holds:

- No `SecurityContext` is present in the GraphQL context
- `Authentication` is `null`
- `Authentication.isAuthenticated()` returns `false`
- The token is an `AnonymousAuthenticationToken`

| Outcome | Result |
|---|---|
| Caller is authenticated and not anonymous | `DataFetcher` executes normally |
| Caller is anonymous, missing, or unauthenticated | `AccessDeniedException("Access denied: authentication required")` |

Spring's exception handling translates `AccessDeniedException` into a GraphQL error response — same flow as `@requiresScopes`.

`@authenticated` and `@requiresScopes` compose freely on the same field. Each directive is enforced by an independent instrumentation, and both must pass for the original `DataFetcher` to run:

```graphql
type Query {
    auditLog: AuditLog @authenticated @requiresScopes(scopes: [["role:ADMIN"]])
}
```

The directive carries no arguments and has no SPI — the predicate (anonymous/null/unauthenticated → deny) is fixed.

---

## Cross-compatibility with Apollo Router / Hive Gateway

The `@requiresScopes` directive itself is identical across Apollo Router, Hive Gateway, and this library — same syntax, same OR-of-AND semantics, same case-sensitive comparison. What differs is the **source** of the scope set each runtime matches against:

| Runtime | Scope source | Typical schema |
|---|---|---|
| Apollo Router | JWT `scope` claim, literal tokens | `@requiresScopes(scopes: [["admin"]])` |
| Hive Gateway | JWT `scope` claim (configurable), literal tokens | `@requiresScopes(scopes: [["admin"]])` |
| This library (zero config) | Spring Security's `GrantedAuthority` set (`SCOPE_` prefix by default) | `@requiresScopes(scopes: [["admin"]])` ✓ same schema works |
| This library (with `scope-mappings`) | Above **plus** per-claim authority prefixes | `@requiresScopes(scopes: [["feature:PRICING"]])` (library-specific extension) |

Schemas written with raw Apollo scope values (`"admin"`, `"read:user"`) are portable across all three runtimes. The `type:VALUE` convention (`"role:ADMIN"`, `"feature:PRICING"`) is this library's extension on top of Spring Security's prefixed authority model and is **not** portable to Apollo Router or Hive Gateway — those runtimes would look for those literal strings in the JWT scope set.

### Writing a portable schema

A schema using only raw OAuth2 scope tokens runs unchanged across all three runtimes:

```graphql
directive @requiresScopes(scopes: [[String!]!]!) on FIELD_DEFINITION

type Query {
    catalog: Catalog    @requiresScopes(scopes: [["read:catalog"]])
    inventory: Inventory @requiresScopes(scopes: [["read:catalog", "admin"], ["superuser"]])
}
```

With a JWT carrying `{"scope": "read:catalog admin"}`:
- **Apollo Router** — checks set `{"read:catalog", "admin"}` contains all tokens from one AND group. Passes.
- **Hive Gateway** — same mechanism.
- **This library (zero config)** — authorities are `SCOPE_read:catalog, SCOPE_admin`. `ScopePrefixAuthorityMatchStrategy` prepends `SCOPE_` to each scope token in the directive; matches.

No configuration needed on any side. The library-specific `type:VALUE` convention exists only for applications that want to map additional JWT claims (roles, features, groups) into Spring Security's prefixed authority conventions — something Apollo Router doesn't do by default and Hive Gateway handles via Rhai scripts.

---

## End-to-End Example

A complete walkthrough of a Spring Boot application using this library, from project setup through a successful and a denied request.

### 1. Project setup

`build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.clutcher.security:spring-security-graphql-requiresscopes-starter:1.0.0")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-web")
}
```

### 2. Application configuration

`src/main/resources/application.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com/
  graphql:
    schema:
      locations: classpath:graphql/
```

No `spring.security.graphql.requiresscopes.*` configuration is needed for Apollo-shaped schemas. The library reads the standard Spring Boot JWT properties and bridges them to the directive.

### 3. GraphQL schema

`src/main/resources/graphql/schema.graphqls`:

```graphql
directive @requiresScopes(scopes: [[String!]!]!) on FIELD_DEFINITION

type Query {
    publicInfo: String
    adminPanel: String @requiresScopes(scopes: [["admin"]])
}
```

### 4. Data fetcher

```java
@Controller
public class ExampleController {

    @QueryMapping
    public String publicInfo() {
        return "anyone can see this";
    }

    @QueryMapping
    public String adminPanel() {
        return "admin-only content";
    }
}
```

No annotation-based security is needed on the methods themselves — the directive in the schema drives enforcement.

### 5. A successful request

Client obtains a JWT from the issuer with this payload:

```json
{
  "sub": "alice",
  "iss": "https://auth.example.com/",
  "scope": "admin profile",
  "exp": 1735689600
}
```

Client sends the GraphQL request:

```bash
curl -X POST https://api.example.com/graphql \
  -H "Authorization: Bearer eyJhbGciOi..." \
  -H "Content-Type: application/json" \
  -d '{"query": "{ adminPanel }"}'
```

What happens inside the application:

| Stage | Component | Result |
|---|---|---|
| 1 | `BearerTokenAuthenticationFilter` | Extracts the JWT from the `Authorization` header |
| 2 | `JwtDecoder` (configured by issuer-uri) | Validates signature and claims; produces a `Jwt` |
| 3 | Spring Security's default `JwtGrantedAuthoritiesConverter` | Reads `scope` claim, produces authorities `[SCOPE_admin, SCOPE_profile]` |
| 4 | `SecurityContextHolder` | Holds an `Authentication` with those authorities; Spring GraphQL forwards it into the `GraphQLContext` |
| 5 | `RequiresScopesInstrumentation.instrumentDataFetcher` | Sees the `@requiresScopes` directive on `adminPanel`, extracts scope values `[["admin"]]` |
| 6 | Wrapped `DataFetcher` | Resolves `Authentication` from the context, calls `enforceScopes(auth, [["admin"]])` |
| 7 | `ScopePrefixAuthorityMatchStrategy.check(auth, "admin")` | Prepends configured prefix → looks up `SCOPE_admin`; found → returns `true` |
| 8 | Enforcement passes | Original data fetcher runs, returns `"admin-only content"` |

Response:

```json
{ "data": { "adminPanel": "admin-only content" } }
```

### 6. A denied request

Same schema, same endpoint, but the client's JWT has:

```json
{ "sub": "bob", "scope": "profile", "exp": 1735689600 }
```

Bob lacks the `admin` scope. Stage 7 in the table above becomes:

| 7 | `ScopePrefixAuthorityMatchStrategy.check(auth, "admin")` | Looks up `SCOPE_admin`; Bob's authorities are `[SCOPE_profile]`; returns `false` |
| 8 | All strategies return `false` for the AND group; no other group | `AccessDeniedException("Access denied: insufficient scopes")` thrown from the fetcher |

Spring's GraphQL exception handling converts the exception into a GraphQL error:

```json
{
  "errors": [
    {
      "message": "Exception while fetching data (/adminPanel) : Access denied: insufficient scopes",
      "path": ["adminPanel"]
    }
  ],
  "data": { "adminPanel": null }
}
```

### 7. Layering on additional claim mappings

Suppose the JWT issuer also provides a `features` claim and you want `@requiresScopes(scopes: [["feature:PRICING"]])` in the schema. Add:

```yaml
spring:
  security:
    graphql:
      requiresscopes:
        scope-mappings:
          features: FEATURE_
```

A JWT like:

```json
{ "scope": "admin", "features": ["PRICING", "SEARCH"] }
```

Now yields authorities `[SCOPE_admin, FEATURE_PRICING, FEATURE_SEARCH]`. Schemas can mix both conventions:

```graphql
type Query {
    adminPanel: String @requiresScopes(scopes: [["admin"]])                      # Apollo shape — works via ScopePrefixAuthorityMatchStrategy
    pricing: String    @requiresScopes(scopes: [["feature:PRICING"]])            # library extension — works via ClaimPrefixMappingStrategy
    dashboard: String  @requiresScopes(scopes: [["admin", "feature:PRICING"]])   # mixed — both must pass
}
```

The `scope`/`scp` → `SCOPE_` default converter keeps running alongside the per-claim converter for `features`, so Apollo-shape and library-extension scopes coexist in one schema.

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

## Troubleshooting

**Every `@requiresScopes`-protected field denies access, even with a valid token**

- Check that `spring-boot-starter-oauth2-resource-server` is on the classpath and `spring.security.oauth2.resourceserver.jwt.issuer-uri` (or `public-key-location` / `jwk-set-uri`) is configured. Without JWT decoding, no authorities are produced and every protected field denies.
- Verify the `Authorization: Bearer <token>` header reaches the server (check for a CORS preflight or proxy that strips it).
- Decode the JWT payload and confirm the expected claim is present and non-empty.

**My Apollo-shape schema `@requiresScopes(scopes: [["admin"]])` denies access**

- The strategy that bridges Apollo scopes is registered only when `authorities-claim-name` is unset, `"scope"`, or `"scp"`. If you set it to anything else (`roles`, `groups`, …), the bridge is disabled by design; switch to the `type:VALUE` convention (e.g. `"role:admin"`) with a matching `scope-mappings` entry.
- Confirm the JWT actually contains a `scope` (or `scp`) claim. Some identity providers put scopes under a different key.
- Confirm case matches: Apollo/OAuth2 scope strings and the authority values Spring Security produces are **case-sensitive**. `"admin"` in the schema must case-match `"admin"` in the JWT scope claim.

**`@requiresScopes(scopes: [["role:ADMIN"]])` doesn't match even though the user has `ROLE_ADMIN`**

- Check the case of the value part: `"role:ADMIN"` resolves to authority `"ROLE_ADMIN"` (strict equals); `"role:admin"` resolves to `"ROLE_admin"` and will NOT match `"ROLE_ADMIN"`.
- If you configured a custom role prefix via `GrantedAuthorityDefaults`, confirm the JWT converter is producing authorities with that prefix (the library reads `GrantedAuthorityDefaults` automatically, but your `JwtAuthenticationConverter` must also be applying it).

**`@authenticated` denies a request that I expected to pass**

- Confirm the `Authorization: Bearer <token>` header is reaching the server and the JWT decodes successfully — without a valid token Spring Security installs an `AnonymousAuthenticationToken`, which `@authenticated` rejects by design.
- The directive denies on `null`, `!isAuthenticated()`, **and** `AnonymousAuthenticationToken`. A custom `AuthenticationManager` that returns an `AnonymousAuthenticationToken` for valid users will still be denied — return a non-anonymous token (e.g. `JwtAuthenticationToken`, `UsernamePasswordAuthenticationToken`) instead.
- Make sure the `SecurityContext` is populated in the GraphQL context. Spring Security's GraphQL integration handles this upstream; a custom WebGraphQlInterceptor that bypasses it will leave the context empty and `@authenticated` will deny.

**My JWT has a `roles` claim but those roles don't appear as authorities**

- Spring Security's default converter reads only the `scope`/`scp` claim. To have `roles` claim entries become authorities, either set `spring.security.oauth2.resourceserver.jwt.authorities-claim-name=roles` (single-claim override, Spring Boot built-in) or add `roles: ROLE_` to `scope-mappings` (multi-claim, additive via this library).

**I provided my own `JwtAuthenticationConverter` bean and `scope-mappings` stopped working**

- Our autoconfig's `JwtAuthenticationConverter` bean is guarded by `@ConditionalOnMissingBean`. When you provide your own, ours is suppressed entirely — including the per-claim converters for `scope-mappings` entries. If you need both your custom logic and the library's per-claim converters, compose them in your custom converter rather than relying on ours.

**Which bean wins? (precedence summary)**

1. A consumer-provided `@Bean JwtAuthenticationConverter` always wins (both this library's and Spring Boot's autoconfigs back off).
2. This library's `JwtAuthenticationConverter` is registered only when `scope-mappings` is non-empty; it reads Spring Boot's `spring.security.oauth2.resourceserver.jwt.*` properties to configure the default converter layer.
3. When `scope-mappings` is empty, the library registers no `JwtAuthenticationConverter` bean. Spring Boot's own autoconfig (or Spring Security's internal default) applies — the `scope`/`scp` → `SCOPE_` pipeline runs unchanged.
4. `ScopePrefixAuthorityMatchStrategy` is registered only when `authorities-claim-name` is unset, `"scope"`, or `"scp"`.
5. `SimpleAuthorityMatchStrategy` and `ClaimPrefixMappingStrategy` are always registered; consumers replace them by providing a bean of the same concrete type.

---

## Implementation Details

### spring-security-graphql-requiresscopes

- **`RequiresScopesInstrumentation`** — Extends `SimplePerformantInstrumentation`. Wraps every field's `DataFetcher` via `instrumentDataFetcher`. Fields without `@requiresScopes` are returned unchanged (zero overhead). The `scopes` argument is extracted once outside the returned lambda, so the AST walk happens per-field-definition, not per-fetch.
- **`AuthenticatedInstrumentation`** — Extends `SimplePerformantInstrumentation`. Wraps every field's `DataFetcher`; fields without `@authenticated` are returned unchanged (zero overhead). Resolves `Authentication` from the GraphQL context and denies access when the caller is anonymous, missing, or unauthenticated. No SPI: the predicate is fixed.
- **`ScopeCheckStrategy`** — SPI interface: `check(Authentication, String) -> boolean`. Strategies are tried in the order Spring injects them and short-circuit on first `true`.
- **`SimpleAuthorityMatchStrategy`** — Delegates to `AuthorityMatcher`: passes if any authority equals the scope value verbatim (case-sensitive).
- **`ScopePrefixAuthorityMatchStrategy`** — Prepends a configured authority prefix to the scope, then delegates to `AuthorityMatcher`. Bridges Apollo-style raw scopes to Spring Security's prefixed authorities.
- **`ClaimPrefixMappingStrategy`** — Map-driven prefix transformation built from the auto-registered `"role:"` entry plus the `scope-mappings` property. Delegates final authority comparison to `AuthorityMatcher`.
- **`AuthorityMatcher`** — Shared utility in `dev.clutcher.security.graphql.utils`. Centralises the authority-comparison rule: strict case-sensitive `String.equals`. A policy change is applied here once. Aligned with RFC 6749 §3.3 (OAuth 2.0 scopes are case-sensitive), Apollo Router's `@requiresScopes` implementation, and Spring Security's `AuthoritiesAuthorizationManager`.

### spring-security-graphql-requiresscopes-starter

`SchemaSecurityAutoConfiguration` creates the following beans (each guarded by `@ConditionalOnMissingBean` on its concrete class):

1. **`SimpleAuthorityMatchStrategy`** — Always registered. No configuration required.
2. **`ScopePrefixAuthorityMatchStrategy`** — Registered when `spring.security.oauth2.resourceserver.jwt.authorities-claim-name` is unset, `"scope"`, or `"scp"`. Constructed with `OAuth2ResourceServerProperties.Jwt.authorityPrefix` (default `"SCOPE_"`).
3. **`ClaimPrefixMappingStrategy`** — Always registered. Built from a combined `LinkedHashMap` containing the auto-registered `"role:"` entry (prefix resolved from the `GrantedAuthorityDefaults` bean, or Spring Security's default `"ROLE_"` if none is declared) followed by every `scope-mappings` entry. A user-supplied `role:` entry in `scope-mappings` overrides the auto-registered one.
4. **`JwtAuthenticationConverter`** — Registered **only when `scope-mappings` is non-empty**. Otherwise Spring Security's default pipeline applies. When registered, the converter composes: first, a `JwtGrantedAuthoritiesConverter` mirroring Spring Security's defaults but seeded from `OAuth2ResourceServerProperties.Jwt` (respects user-set `authority-prefix`, `authorities-claim-name`, `authorities-claim-delimiter`); then, one per `scope-mappings` entry. This ensures `scope-mappings` is additive on top of the default converter, never a replacement.
5. **`RequiresScopesInstrumentation`** — Always registered when graphql-java is on the classpath. Constructed with the injected list of `ScopeCheckStrategy` beans.
6. **`AuthenticatedInstrumentation`** — Always registered when graphql-java is on the classpath. No configuration required.

---

## Specification References

- [Apollo Federation `@requiresScopes` directive](https://www.apollographql.com/docs/graphos/reference/federation/directives) — the directive's canonical definition.
- [Apollo Router authorization](https://www.apollographql.com/docs/graphos/routing/security/authorization) — reference implementation whose scope-matching semantics this library mirrors.
- [RFC 6749 §3.3](https://datatracker.ietf.org/doc/html/rfc6749#section-3.3) — OAuth 2.0 access token scope format; defines scopes as case-sensitive strings.
- [Spring Security `GrantedAuthority`](https://docs.spring.io/spring-security/reference/servlet/authorization/architecture.html) — the authority model this library binds the directive to.
- [Spring Boot OAuth2 resource server properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html) — `spring.security.oauth2.resourceserver.jwt.*` (authority-prefix, authorities-claim-name, authorities-claim-delimiter).
