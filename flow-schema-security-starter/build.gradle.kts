project.description = "Spring Boot starter for flow-schema-security library."

plugins {
    id("java-library")
}

dependencies {
    api(project(":flow-schema-security"))

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-web")
}
