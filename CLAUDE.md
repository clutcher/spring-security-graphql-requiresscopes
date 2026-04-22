# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

Gradle multi-project build. Java toolchain 24, Spring Boot 4.0.2, graphql-java 25.

```bash
./gradlew build                                            # compile + test all modules
./gradlew :spring-security-graphql-requiresscopes:test     # test core only
./gradlew test --tests "ClaimPrefixMappingStrategyTest"    # single test class
./gradlew test --tests "*Test.shouldReturnTrueWhen*"       # single test method (pattern)
./gradlew publishToMavenLocal                              # install both artifacts to ~/.m2
./gradlew jreleaserFullRelease                             # Maven Central release (needs GITHUB_TOKEN, MAVENCENTRAL_USERNAME, MAVENCENTRAL_PASSWORD, GPG)
```

## Architecture

Two-module Gradle build publishing two Maven artifacts under `dev.clutcher.security`:

- **`spring-security-graphql-requiresscopes`** — core library. Contains the `ScopeCheckStrategy` SPI, three strategy implementations (`SimpleAuthorityMatchStrategy`, `ScopePrefixAuthorityMatchStrategy`, `ClaimPrefixMappingStrategy`), the `AuthorityMatcher` helper, and `RequiresScopesInstrumentation`. Dependencies on Spring Security and graphql-java are `compileOnly` — the consuming app supplies them.
- **`spring-security-graphql-requiresscopes-starter`** — Spring Boot autoconfig. `api`-depends on the core module. Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### Enforcement flow

`RequiresScopesInstrumentation` extends `SimplePerformantInstrumentation` and wraps every field's `DataFetcher` via `instrumentDataFetcher`. If the field definition has no `@requiresScopes` applied directive, the fetcher is returned unchanged (zero overhead). Otherwise the `scopes` argument is extracted once (outside the returned lambda, so the AST walk happens per-field-definition, not per-fetch) and a wrapping fetcher enforces scopes before delegating.

The `scopes` argument can arrive as an AST literal (`ArrayValue` of `ArrayValue` of `StringValue`) or as a coerced external `List<List<String>>`. Both shapes are handled in `extractScopes`.

Evaluation is OR-of-AND (Apollo Federation semantics): outer list = OR, inner list = AND. A single scope passes if **any** `ScopeCheckStrategy` in the injected list returns true. On failure: `AccessDeniedException("Access denied: insufficient scopes")` — Spring translates this into a GraphQL error; no extra wiring required.

`Authentication` is read from the GraphQL context under key `SecurityContext.class.getName()` — populated by Spring Security's GraphQL integration upstream.

### Strategy SPI

`ScopeCheckStrategy.check(Authentication, String) -> boolean`. Strategies are tried in the order Spring injects them; short-circuits on first `true`. Three autoconfigured beans (each guarded by `@ConditionalOnMissingBean` on its **concrete class**):

1. `SimpleAuthorityMatchStrategy` — delegates to `AuthorityMatcher.hasAuthority(...)` with the scope value verbatim. No config. Always registered.
2. `ScopePrefixAuthorityMatchStrategy` — prepends the Spring-Security-configured `authority-prefix` (default `"SCOPE_"`) to the scope and delegates to `AuthorityMatcher`. Bridges Apollo-style raw scopes (`@requiresScopes(scopes: [["admin"]])`) to prefixed authorities (`SCOPE_admin`). **Registered only when `spring.security.oauth2.resourceserver.jwt.authorities-claim-name` is unset, `"scope"`, or `"scp"`** — i.e. when Spring Security is reading the OAuth2 scope claim. When the user retargets the converter to a different claim (`roles`, `groups`, …), this strategy is not registered because the Apollo-shape semantic no longer applies.
3. `ClaimPrefixMappingStrategy` — built from a `LinkedHashMap` containing an auto-registered `"role:"` entry (prefix resolved from `GrantedAuthorityDefaults` if the bean is present, else Spring Security's default `"ROLE_"` literal) followed by every entry from the `scope-mappings` property. Iteration order is the matching order; first prefix match wins. A user-supplied `role:` entry in `scope-mappings` overrides the auto-registered one (insertion-then-overlay). If `GrantedAuthorityDefaults` is constructed with a null prefix, the `Optional.map(...).orElse("ROLE_")` chain silently falls back to `"ROLE_"` — matching Spring Security's own lack of construction-time validation on `GrantedAuthorityDefaults`.

All strategies ultimately compare via `AuthorityMatcher.hasAuthority(Authentication, String)` in `dev.clutcher.security.graphql.utils`. The matcher uses strict case-sensitive `String.equals` — aligning with RFC 6749 §3.3 (OAuth2 scopes are case-sensitive), Apollo Router's reference implementation of `@requiresScopes` (uses `HashSet.is_superset` on `String`), and Spring Security's `AuthoritiesAuthorizationManager` (uses `Collection.contains`). Keeping the authority-comparison rule in one place means a policy change is applied in one location.

Consumers register a custom strategy by declaring any `ScopeCheckStrategy` bean. Replacing a default is done by supplying a bean of that exact concrete type — note there is no separate bean for role-prefix handling; replacing `ClaimPrefixMappingStrategy` replaces all prefix-transform logic including the role entry.

### JWT converter

`SchemaSecurityAutoConfiguration` registers a `JwtAuthenticationConverter` bean **only when `scope-mappings` is non-empty**. When empty, Spring Security's internal default pipeline applies unchanged (scope/scp claim → `SCOPE_` prefix, or whatever Spring Boot's `spring.security.oauth2.resourceserver.jwt.*` properties configure). When non-empty, our converter composes: first, a `JwtGrantedAuthoritiesConverter` seeded from `OAuth2ResourceServerProperties.Jwt` (respects user-configured `authority-prefix`, `authorities-claim-name`, `authorities-claim-delimiter`; falls back to Spring Security defaults when properties are unset); then one per `scope-mappings` entry. `scope-mappings` is additive on top of the default converter, never a replacement. Also guarded by `@ConditionalOnMissingBean(JwtAuthenticationConverter.class)` so an application's own converter always wins.

### Configuration surface

All library config under `spring.security.graphql.requiresscopes` (`RequiresScopesProperties`):
- `scope-mappings` — `Map<String,String>`, key is claim/scope-type name **without** trailing colon (the autoconfig appends `:` when merging into `ClaimPrefixMappingStrategy`).

The library also **reads** Spring Boot's OAuth2 resource server JWT properties and adapts accordingly:
- `spring.security.oauth2.resourceserver.jwt.authority-prefix` — controls `ScopePrefixAuthorityMatchStrategy`'s prefix and the default JWT converter's prefix.
- `spring.security.oauth2.resourceserver.jwt.authorities-claim-name` — determines whether `ScopePrefixAuthorityMatchStrategy` is registered (only for unset, `"scope"`, or `"scp"`).
- `spring.security.oauth2.resourceserver.jwt.authorities-claim-delimiter` — applied to the default converter when building the composite.

To customise the role authority prefix, declare a `GrantedAuthorityDefaults` bean (standard Spring Security mechanism). The library does not provide a YAML-level override for role prefix — mirrors Spring Security's own convention of going through `GrantedAuthorityDefaults`.

## Package layout (core library)

- `graphql.instrumentation` — `RequiresScopesInstrumentation`
- `graphql.strategy` — `ScopeCheckStrategy` SPI interface only
- `graphql.strategy.impl` — `SimpleAuthorityMatchStrategy`, `ScopePrefixAuthorityMatchStrategy`, `ClaimPrefixMappingStrategy`
- `graphql.utils` — `AuthorityMatcher` (shared authority-comparison rule)

New strategy implementations go in `strategy/impl/`. Anything shared across strategies goes in `utils/`.

## Conventions

- No comments in production or test code. Javadoc only where the WHY is genuinely non-obvious — `AuthorityMatcher` (matching rule rationale), `ScopeCheckStrategy` (how to register custom implementations), `RequiresScopesInstrumentation` (context key). Do not add Javadoc to straightforward implementations.
- Tests use JUnit 5. Naming: `should<Outcome>When<Condition>`, with `// Given / // When / // Then` structure. Use `TestingAuthenticationToken` to construct `Authentication` — see `SimpleAuthorityMatchStrategyTest` as the reference pattern.
- `RequiresScopesInstrumentationTest` tests via real GraphQL schema execution (SDL parsed at test time, no mocks). Use this pattern for any instrumentation-level test — mocking `DataFetchingEnvironment` and `GraphQLAppliedDirective` is impractical.
- Core library must stay framework-thin: add Spring/graphql-java deps as `compileOnly` in `spring-security-graphql-requiresscopes/build.gradle.kts`, and mirror them as `testImplementation`. Runtime/`implementation` deps belong in the starter module.
- Adding a property to `RequiresScopesProperties` automatically generates IDE completion metadata via `spring-boot-configuration-processor` (already wired in the starter's `build.gradle.kts`).
- Version is set once in the root `build.gradle.kts` (`allprojects { version = ... }`); do not set per-subproject.

## Design decisions (why the code is shaped this way)

The following choices are non-obvious from reading the code. Understand them before proposing structural changes.

**Three strategy classes kept separate (not unified).** `SimpleAuthorityMatchStrategy` (verbatim) and `ScopePrefixAuthorityMatchStrategy` (prepend configured prefix) could be collapsed into one parametric class, but the separation is deliberate: each has a distinct public API surface (`@ConditionalOnMissingBean` by concrete class), a distinct trigger condition (Simple is always registered; ScopePrefix is conditionally registered), and a distinct Javadoc rationale. Merging would force a breaking API change and lose the conditional-registration semantic.

**`scope-mappings` is additive, not replacing Spring Security's defaults.** Earlier versions built the JWT converter from `scope-mappings` alone, producing zero authorities when the property was empty — silently worse than stock Spring Security. The current composite always includes a default converter seeded from `OAuth2ResourceServerProperties.Jwt` so Spring Security's `scope`/`scp` → `SCOPE_` pipeline keeps running alongside per-claim additions.

**Custom `@Conditional` classes (`OAuth2ScopeClaimCondition`, `NonEmptyScopeMappingsCondition`) instead of `@ConditionalOnExpression` or null-returning `@Bean` methods.** `@ConditionalOnExpression` with SpEL for multi-value checks is unreadable. Returning `null` from `@Bean` methods registers a "null bean" that interacts awkwardly with `AssertableApplicationContext.doesNotHaveBean(...)` and with collection injection. Explicit `SpringBootCondition` subclasses are verbose but unambiguous and produce good diagnostic messages via `ConditionOutcome`.

**`AuthorityMatcher` stays pure.** It has no configuration, no state, no prefix awareness — just strict case-sensitive `String.equals`. All transformation happens inside strategies. Putting prefix knowledge in the matcher would affect `ClaimPrefixMappingStrategy` (which has already transformed its value before calling the matcher) and create double-prefixing bugs.

**`@EnableConfigurationProperties(OAuth2ResourceServerProperties.class)` on our autoconfig.** Idempotent with Spring Boot's own declaration; ensures the properties are bound even in test contexts that don't load Spring Boot's full oauth2 autoconfig stack.

## Testing patterns

**Autoconfig tests use `ApplicationContextRunner`, not `@SpringBootTest`.** Full context tests are overkill for autoconfig; `ApplicationContextRunner` from `spring-boot-test` spins up a minimal context per test, allowing fast per-test property overrides. Skeleton:

```java
private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SchemaSecurityAutoConfiguration.class))
        .withUserConfiguration(OAuth2PropertiesEnablingConfig.class);

@Test
void shouldRegisterXBeanUnderYCondition() {
    contextRunner
            .withPropertyValues("some.property=value")
            .run(context -> assertThat(context).hasSingleBean(SomeType.class));
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OAuth2ResourceServerProperties.class)
static class OAuth2PropertiesEnablingConfig {}
```

See `SchemaSecurityAutoConfigurationTest` for the full pattern — bean-presence assertions, strategy-behaviour assertions via injected `List<ScopeCheckStrategy>`, and JWT-conversion assertions via the `JwtAuthenticationConverter` bean.

**Instrumentation-level tests use real GraphQL schema execution.** See `RequiresScopesInstrumentationTest` — parses SDL at test time, builds a `GraphQL` instance with the instrumentation, runs queries with `TestingAuthenticationToken` installed in the `GraphQLContext`. Do not mock `DataFetchingEnvironment` or `GraphQLAppliedDirective`; their APIs are too fluid and coupled to the instrumentation's AST walk.

**Strategy unit tests are pure JUnit 5**, no Spring. See `SimpleAuthorityMatchStrategyTest` as the reference — field-initialised strategy instance, private `authWith(...)` helper for `TestingAuthenticationToken`, `// Given / // When / // Then` structure.

## Known gotchas

- **Spring Security 7's `JwtAuthenticationConverter` appends a `FACTOR_BEARER` authority** to every token it produces, representing the authentication factor. When asserting authorities from the converter, use `.contains("X", "Y")` not `.containsExactlyInAnyOrder(...)` — otherwise the extra factor authority breaks the assertion.
- **Spring Boot 4.x split the autoconfig module.** `OAuth2ResourceServerProperties` moved from `org.springframework.boot.autoconfigure.security.oauth2.resource` to `org.springframework.boot.security.oauth2.server.resource.autoconfigure`. The artifact is `spring-boot-security-oauth2-resource-server`, added as `compileOnly` in the starter's `build.gradle.kts`.
- **`Optional.map(mapper)` returns empty when the mapper returns null.** In `claimPrefixMappingStrategy`, `grantedAuthorityDefaults.map(GrantedAuthorityDefaults::getRolePrefix).orElse("ROLE_")` yields `"ROLE_"` when the bean is absent OR when it returns a null prefix. This matches Spring Security's own lack of construction-time validation on `GrantedAuthorityDefaults(null)`.
- **Returning `null` from a `@Bean` method registers a null bean**, which is filterable from collection injection but can confuse `context.doesNotHaveBean(Class)` assertions. Use `@Conditional` classes instead when you need to conditionally skip bean registration based on runtime properties.

## Specification anchors

When touching matching semantics or scope evaluation, these are the authoritative sources the current behaviour traces to:

- **RFC 6749 §3.3** (OAuth 2.0 scopes are case-sensitive) → `AuthorityMatcher` uses strict `String.equals`.
- **Apollo Router `apollo-router/src/plugins/authorization/scopes.rs`** (`HashSet::is_superset` on `String`) → `AuthorityMatcher` strict equals; `ScopePrefixAuthorityMatchStrategy` reading `OAuth2ResourceServerProperties.Jwt.authorityPrefix` to bridge raw scopes to prefixed authorities.
- **Spring Security `AuthoritiesAuthorizationManager`** (`Collection.contains` via `String.equals`) → same strict rule.
- **Spring Security `JwtGrantedAuthoritiesConverter`** (`WELL_KNOWN_AUTHORITIES_CLAIM_NAMES = ["scope", "scp"]`, `DEFAULT_AUTHORITY_PREFIX = "SCOPE_"`) → our conditional registers `ScopePrefixAuthorityMatchStrategy` only when the configured claim name falls within that default set.

## Plan file

The design rationale for the current autoconfig shape — including the target behaviour contract, alternatives considered, and verification strategy — is captured in `~/.claude-personal/plans/plan-all-fixes-encapsulated-hejlsberg.md`. Consult it when making structural changes to the autoconfig or strategy set.
