plugins {
    kotlin("jvm") version "1.9.22"
    id("com.google.protobuf") version "0.9.4"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

application {
    mainClass.set("org.spruce.gateway.GatewayServer")
}

repositories {
    mavenCentral()
}

val grpcVersion = "1.62.2"
val protobufVersion = "3.25.3"
val jedisVersion = "5.1.0"
val jacksonVersion = "2.17.0"

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))

    // Redis
    implementation("redis.clients:jedis:$jedisVersion")

    // Jackson for JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.12")

    // gRPC + Kotlin stub
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")

    implementation(project(":spruce-api"))
    implementation(project(":spruce-proto"))

    implementation("io.grpc:grpc-netty-shaded:1.64.0")
    implementation("io.grpc:grpc-protobuf:1.64.0")
    implementation("io.grpc:grpc-stub:1.64.0")

    implementation("com.google.protobuf:protobuf-java:3.25.2")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        archiveClassifier.set("")
        manifest {
            attributes["Main-Class"] = application.mainClass.get()
        }
    }

    jar {
        enabled = false
    }

    named<CreateStartScripts>("startScripts") {
        dependsOn(shadowJar)
    }
    named("distZip") {
        dependsOn(shadowJar)
    }
    named("distTar") {
        dependsOn(shadowJar)
    }
    named("startShadowScripts") {
        dependsOn(shadowJar)
    }

    build {
        dependsOn(shadowJar)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
