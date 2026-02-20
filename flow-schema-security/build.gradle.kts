project.description = "Core library for feature-based access control on the GraphQL Federation schema layer."

dependencies {
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("org.springframework.graphql:spring-graphql")
    compileOnly("com.graphql-java:graphql-java")

    testImplementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.graphql:spring-graphql")
    testImplementation("com.graphql-java:graphql-java")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
