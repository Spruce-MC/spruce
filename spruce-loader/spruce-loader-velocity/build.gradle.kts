plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("kapt")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }
}

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")

    implementation(project(":spruce-core"))
    implementation(project(":spruce-api"))
    implementation(project(":spruce-loader:spruce-loader-commons"))

    kapt("com.velocitypowered:velocity-api:3.2.0-SNAPSHOT")
}
