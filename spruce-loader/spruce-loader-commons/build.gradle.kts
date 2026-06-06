plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

tasks {
    shadowJar {
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }
}

dependencies {
    implementation(project(":spruce-core"))
    implementation(project(":spruce-api"))
}
