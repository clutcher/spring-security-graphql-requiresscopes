project.description = "Spring Boot starter for spring-security-graphql-requiresscopes library."

plugins {
    id("java-library")
}

dependencies {
    api(project(":spring-security-graphql-requiresscopes"))

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")

    // Needed for @ConditionalOnClass — graphql-java is provided by the consuming application at runtime.
    compileOnly("com.graphql-java:graphql-java:25.0")

    compileOnly("org.springframework.security:spring-security-oauth2-jose")

    // Generates spring-configuration-metadata.json for IDE autocomplete of requiresscopes.* properties.
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}