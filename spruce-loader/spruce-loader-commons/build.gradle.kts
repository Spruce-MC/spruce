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

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")
}
