import com.google.protobuf.gradle.id

plugins {
    `java-library`
    id("com.google.protobuf") version "0.10.0"
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/groundsgg/*")
        credentials {
            username =
                providers.gradleProperty("github.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password =
                providers.gradleProperty("github.token").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

// Mirrors :api — the root convention puts Kotlin on 25, and the generated Java
// stubs would then compile against a different target than the Kotlin stub-gen
// task the same convention adds.
tasks.withType<JavaCompile> { options.release.set(21) }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}

dependencies {
    api("io.grpc:grpc-protobuf:1.78.0")
    api("io.grpc:grpc-stub:1.78.0")
    api("com.google.protobuf:protobuf-java:4.35.1")
    // javax.annotation.Generated, which protoc-gen-grpc-java emits and the JDK
    // no longer ships. compileOnly: nothing reads the annotation at runtime.
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    // Proto-only jar: it ships config_service.proto and config_admin.proto and no
    // compiled classes, so the stubs are generated here from the pinned contract
    // rather than inherited from an artifact that could drift from it.
    protobuf("gg.grounds:library-grpc-contracts-config:0.2.0")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.35.1" }
    plugins { id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.78.0" } }
    generateProtoTasks { all().forEach { it.plugins { id("grpc") } } }
}
