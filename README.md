# flow-schema-security

A Spring Boot library for feature-based access control on the GraphQL Federation schema layer.

Analogous to Spring Security, but for GraphQL schema fields and queries — enforcing `enabledFeatures` checks at the API level rather than the UI level.

## Project Structure

- **`flow-schema-security`** — Core library: `FeatureAuthorizer` interface, directive wiring, provider abstraction
- **`flow-schema-security-starter`** — Spring Boot starter: auto-configuration, properties, HTTP client wiring to user-management

## Installation

```kotlin
implementation("com.abb.flow:flow-schema-security-starter:1.0.0")
```

## Usage

Add `@authenticated` and `@requiresScopes` directives to your subgraph schema, and use `@featureAuthorizer` in `@PreAuthorize` on controllers.

See [ADR-017](/docs/Decisions/&-017.-Feature-Based-Access-Control-at-API-Level.md) for the architectural decision.
