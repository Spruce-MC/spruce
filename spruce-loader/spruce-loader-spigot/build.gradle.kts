plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
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

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")

    implementation(project(":spruce-core"))
    implementation(project(":spruce-api"))
    implementation(project(":spruce-loader:spruce-loader-commons"))
}
