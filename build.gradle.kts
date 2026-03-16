plugins {
    id("java")
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
    id("signing")
}

extra["springBootVersion"] = "4.0.2"

allprojects {
    group = "dev.clutcher.security"
    version = "1.0.0"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(24)
        }
        withSourcesJar()
        withJavadocJar()
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.mockito:mockito-junit-jupiter:5.18.0")
    }

    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
        }
    }

    configurePublishing()

    tasks.test {
        useJUnitPlatform()
    }
}

fun Project.configurePublishing() {
    publishing {
        publications {
            create<MavenPublication>("default") {
                from(components["java"])
                pom {
                    name.set(project.name)
                    description.set(provider { project.description })
                    url.set("https://github.com/abb-flow/flow-schema-security")
                    licenses {
                        license {
                            name.set("MIT License")
                        }
                    }
                    developers {
                        developer {
                            id.set("abb-flow")
                            name.set("ABB Flow Team")
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                name = "RootStaging"
                url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
            }
        }
    }
}