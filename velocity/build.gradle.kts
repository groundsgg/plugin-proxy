plugins { id("gg.grounds.velocity") version "0.1.1" }

dependencies {
    implementation(project(":api"))
    implementation("io.nats:jnats:2.26.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}
