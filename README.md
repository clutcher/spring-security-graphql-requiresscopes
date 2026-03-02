# flow-schema-security

A Spring Boot library for feature and role-based access control in ABB Flow microservices.

Provides `FeatureAuthorizer` and `RoleAuthorizer` beans that read directly from the JWT already in the security context — no HTTP calls at runtime.

## Project Structure

- **`flow-schema-security`** — Core library: `FeatureAuthorizer` and `RoleAuthorizer` interfaces with their default implementations
- **`flow-schema-security-starter`** — Spring Boot starter: auto-configures both beans; services include this and inject the authorizers

## Installation

```kotlin
implementation("com.abb.flow:flow-schema-security-starter:1.0.0")
```

## Usage

Inject `FeatureAuthorizer` or `RoleAuthorizer` and use them in `@PreAuthorize` expressions or service logic:

```java
@PreAuthorize("@featureAuthorizer.hasFeature(authentication, 'CART_SECTION')")
public Cart getCart(@Argument AccountIdentityInput accountId) { ... }
```

```java
if (!roleAuthorizer.hasRole(authentication, "SPECIALPRICINGGROUP")) {
    throw new AccessDeniedException("...");
}
```

`enabledFeatures` is embedded in the JWT by the Spring Cloud Gateway. For account-specific requests, the gateway parses `accountIds` from the GraphQL request body and calls Hybris with them — the returned features are merged into the flat `enabledFeatures` claim before the JWT reaches subgraphs.
