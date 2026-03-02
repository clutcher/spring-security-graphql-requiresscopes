project.description = "Core library for feature-based access control on the GraphQL Federation schema layer."

dependencies {
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("org.springframework.security:spring-security-oauth2-resource-server")
    compileOnly("org.springframework.security:spring-security-oauth2-jose")

    testImplementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.security:spring-security-oauth2-resource-server")
    testImplementation("org.springframework.security:spring-security-oauth2-jose")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}