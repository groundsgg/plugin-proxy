plugins { `java-library` }

tasks.withType<JavaCompile> { options.release.set(21) }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}

dependencies {
    compileOnly("net.kyori:adventure-api:5.2.0")
    testImplementation("net.kyori:adventure-api:5.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}
