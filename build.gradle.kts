import org.jreleaser.gradle.plugin.JReleaserExtension

plugins {
    id("java")
    id("io.spring.dependency-management") version "1.1.7"

    id("maven-publish")
    id("signing")
    id("org.jreleaser") version "1.23.0"
}

extra["springBootVersion"] = "4.0.2"

allprojects {
    group = "dev.clutcher.spring-security"
    version = "1.0.0"
}

configureJReleaser()

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

    signing {
        useGpgCmd()
        sign(publishing.publications["default"])
    }

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

                    url.set("https://github.com/clutcher/spring-security-graphql-requiresscopes")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://github.com/clutcher/spring-security-graphql-requiresscopes/blob/main/LICENSE")
                            distribution.set("repo")
                        }
                    }

                    developers {
                        developer {
                            id.set("clutcher")
                            name.set("Igor Zarvanskyi")
                            email.set("iclutcher@gmail.com")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/clutcher/spring-security-graphql-requiresscopes.git")
                        developerConnection.set("scm:git:ssh://github.com/clutcher/spring-security-graphql-requiresscopes.git")
                        url.set("https://github.com/clutcher/spring-security-graphql-requiresscopes")
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

fun Project.configureJReleaser() {
    configure<JReleaserExtension> {

        release {
            github {
                repoOwner = "clutcher"
                username = "clutcher"
                token = System.getenv("JRELEASER_GITHUB_TOKEN")

                changelog {
                    formatted.set(org.jreleaser.model.Active.ALWAYS)
                    preset.set("conventional-commits")
                }
            }
        }

        deploy {
            maven {
                mavenCentral {
                    create("sonatype") {
                        setActive("ALWAYS")
                        sign.set(false)

                        url.set("https://central.sonatype.com/api/v1/publisher")
                        stagingRepository("build/staging-deploy")
                    }
                }
            }
        }
    }

    tasks.named("publish") {
        // Add dependencies on each subproject's publish task
        subprojects.forEach { subproject ->
            dependsOn(subproject.tasks.named("publish"))
        }
    }

    tasks.named("publishToMavenLocal") {
        // Add dependencies on each subproject's publish task
        subprojects.forEach { subproject ->
            dependsOn(subproject.tasks.named("publishToMavenLocal"))
        }
    }

    tasks.named("jreleaserFullRelease").configure {
        dependsOn("publish")
    }

    tasks.named("jreleaserDeploy").configure {
        dependsOn("publish")
    }
}