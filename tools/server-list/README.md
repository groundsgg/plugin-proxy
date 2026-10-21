# Real proxy validation

Build with Java 25: `./gradlew :velocity:testClasses :velocity:shadowJar`.
Run `JAVA_HOME=/path/to/jdk python3 tools/server-list/live-harness.py /path/to/velocity.jar /tmp/grounds-motd-proxies --stay`.
Then `python3 tools/server-list/verify-live.py /tmp/grounds-motd-proxies`.

The harness replaces only the test JAR's plugin descriptor. It registers the real production `ServerListPingListener` with separate MotdManagers and stores, a common local configuration file and network presence count. Both real Velocity processes bind only loopback, use no backend game servers, and disable test telemetry. Local baseline counts are 3 and 5; network count is 8. No service tokens or production writes are involved. Ctrl-C terminates the test processes.

Recorded on 2026-10-21 with the locally available Velocity 4.2.0 JAR, SHA-256 `35a5596a5468a035d8a32c8de5ebb0dc6b8d8f0cc3ff5169d514aca762af8aa8`. Responses are retained in `docs/server-list/live-verification.json`. A dedicated test entry point supplies the config and presence inputs; this does not validate live NATS delivery or a production config service deployment.
