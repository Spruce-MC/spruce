dependencies {
    implementation(project(":spruce-api"))

    implementation(project(":spruce-proto"))

    implementation("io.grpc:grpc-netty-shaded:1.64.0")
    implementation("io.grpc:grpc-protobuf:1.64.0")
    implementation("io.grpc:grpc-stub:1.64.0")

    implementation("com.google.protobuf:protobuf-java:3.25.2")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
}
