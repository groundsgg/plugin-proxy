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
    // service-config answers REST; the MOTD store parses its JSON with gson, the
    // way plugin-match's and plugin-social's clients do.
    implementation("com.google.code.gson:gson:2.11.0")
    // The tab list header and footer are player-facing text, so they come from a bundle and are
    // drawn in the design tokens, like every other line the network shows a player.
    implementation("gg.grounds:library-i18n:0.2.0")
    implementation("io.nats:jnats:2.26.0")

    // The metrics endpoint. Micrometer rather than a hand-written exposition format, because its
    // JVM binders publish the same names the services and the game servers do — one Grafana query
    // then covers a proxy, a game server and a service. Shaded into the plugin jar like jnats.
    // HTTP is the JDK's own server, so nothing else is added.
    implementation("io.micrometer:micrometer-registry-prometheus:1.17.0")
    // The transport for the service-config channel. Shaded by gRPC itself, so it does not fight
    // with the Netty the proxy runs on.

    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    // Adventure reaches the plugin through Velocity at runtime, which is compileOnly here and so
    // absent from the test classpath. The tests render real Components, so they need it themselves.
    testImplementation("net.kyori:adventure-api:5.2.0")
    testImplementation("net.kyori:adventure-text-minimessage:5.2.0")
    testImplementation("net.kyori:adventure-text-serializer-plain:5.2.0")
    // Same reason, for the MOTD: the section codes a motd.gg import arrives in, the JSON the
    // stored document is, and the logger the manager reports a failed refresh through.
    testImplementation("net.kyori:adventure-text-serializer-legacy:5.2.0")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.slf4j:slf4j-api:2.0.18")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}
