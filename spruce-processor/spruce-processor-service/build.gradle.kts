plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.9.22-1.0.16"
}

dependencies {
    implementation(project(":spruce-api"))
    implementation(project(":spruce-processor:spruce-processor-commons"))

    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.22-1.0.16")
}
