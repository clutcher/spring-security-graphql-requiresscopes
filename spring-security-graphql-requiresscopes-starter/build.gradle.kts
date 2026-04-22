project.description = "Spring Boot starter for spring-security-graphql-requiresscopes library."

plugins {
    id("java-library")
}

dependencies {
    api(project(":spring-security-graphql-requiresscopes"))

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")

    compileOnly("com.graphql-java:graphql-java:25.0")
    compileOnly("org.springframework.security:spring-security-oauth2-jose")
    compileOnly("org.springframework.boot:spring-boot-security-oauth2-resource-server")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-security-oauth2-resource-server")
    testImplementation("org.springframework.security:spring-security-oauth2-resource-server")
    testImplementation("org.springframework.security:spring-security-oauth2-jose")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}