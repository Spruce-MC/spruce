plugins {
    kotlin("jvm") version "1.9.22" apply false
    id("maven-publish")
}

allprojects {
    group = "org.spruce"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/public/")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/public/")
    }

    extensions.configure<JavaPluginExtension>("java") {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "17"
    }

    afterEvaluate {
        if (name != "spruce-gateway") {
            publishing {
                publications {
                    create<MavenPublication>("mavenJava") {
                        from(components["java"])
                        groupId = project.group.toString()
                        artifactId = project.name
                        version = project.version.toString()
                    }
                }

                repositories {
                    maven {
                        name = "GitHubPackages"
                        url = uri("https://maven.pkg.github.com/Spruce-MC/spruce")
                        credentials {
                            username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                            password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
                        }
                    }
                }
            }
        } else {
            tasks.withType<PublishToMavenLocal>().configureEach {
                enabled = false
            }
        }
    }
}

