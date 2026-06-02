plugins { id("gg.grounds.velocity") version "0.1.1" }

dependencies {
    implementation(project(":api"))
    implementation("io.nats:jnats:2.25.3")
}
