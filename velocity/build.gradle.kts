plugins { id("gg.grounds.velocity") version "0.1.1" }

repositories {
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

dependencies {
    implementation(project(":api"))
    // The tab list header and footer are player-facing text, so they come from a bundle and are
    // drawn in the design tokens, like every other line the network shows a player.
    implementation("gg.grounds:library-i18n:0.2.0")
    implementation("io.nats:jnats:2.26.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    // Adventure reaches the plugin through Velocity at runtime, which is compileOnly here and so
    // absent from the test classpath. The tests render real Components, so they need it themselves.
    testImplementation("net.kyori:adventure-api:4.21.0")
    testImplementation("net.kyori:adventure-text-minimessage:4.21.0")
    testImplementation("net.kyori:adventure-text-serializer-plain:4.21.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}
