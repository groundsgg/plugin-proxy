# Changelog

## [2.2.2](https://github.com/groundsgg/plugin-proxy/compare/v2.2.1...v2.2.2) (2026-09-02)


### Bug Fixes

* balance the space above and below the tab logo ([#56](https://github.com/groundsgg/plugin-proxy/issues/56)) ([4a68550](https://github.com/groundsgg/plugin-proxy/commit/4a685508f6c2c8093154d75984a3a133395e4982))

## [2.2.1](https://github.com/groundsgg/plugin-proxy/compare/v2.2.0...v2.2.1) (2026-09-02)


### Bug Fixes

* give the tab logo more space and reduce its size ([#55](https://github.com/groundsgg/plugin-proxy/issues/55)) ([9830d6e](https://github.com/groundsgg/plugin-proxy/commit/9830d6e8ad3093b7888190fa1315201e458ed99c))
* pad the tab list around the wordmark ([#48](https://github.com/groundsgg/plugin-proxy/issues/48)) ([b012c5a](https://github.com/groundsgg/plugin-proxy/commit/b012c5a971a78eac970bc56cee84748e5acbbdae))

## [2.2.0](https://github.com/groundsgg/plugin-proxy/compare/v2.1.0...v2.2.0) (2026-08-25)


### Features

* brand the tab list with logo, rank chips, and server footer ([#46](https://github.com/groundsgg/plugin-proxy/issues/46)) ([a6ce6de](https://github.com/groundsgg/plugin-proxy/commit/a6ce6dee1051a4c5eff2fa0eacc024cececc3151))

## [2.1.0](https://github.com/groundsgg/plugin-proxy/compare/v2.0.0...v2.1.0) (2026-08-23)


### Features

* **metrics:** publish which platform Bedrock players are on ([#44](https://github.com/groundsgg/plugin-proxy/issues/44)) ([9e2ba6a](https://github.com/groundsgg/plugin-proxy/commit/9e2ba6a358a418fd87f0bdbddc736c2b1a7a5f9a))

## [2.0.0](https://github.com/groundsgg/plugin-proxy/compare/v1.1.1...v2.0.0) (2026-08-10)


### ⚠ BREAKING CHANGES

* **motd:** store the MOTD over HTTP, dropping gRPC ([#40](https://github.com/groundsgg/plugin-proxy/issues/40))

### Features

* **motd:** store the MOTD over HTTP, dropping gRPC ([#40](https://github.com/groundsgg/plugin-proxy/issues/40)) ([9767c9f](https://github.com/groundsgg/plugin-proxy/commit/9767c9f51e5f813817c2992d65e11b763150d033))

## [1.1.1](https://github.com/groundsgg/plugin-proxy/compare/v1.1.0...v1.1.1) (2026-08-04)


### Bug Fixes

* **config:** consume service contract target ([3e046d7](https://github.com/groundsgg/plugin-proxy/commit/3e046d718ee7b59f9529459603b7113c61cce6df))
* **config:** consume service contract target ([e5d5d84](https://github.com/groundsgg/plugin-proxy/commit/e5d5d843f8d23cb982f0883a87a40906c6a859a5))

## [1.1.0](https://github.com/groundsgg/plugin-proxy/compare/v1.0.0...v1.1.0) (2026-08-03)


### Features

* **metrics:** publish proxy metrics on a Prometheus endpoint ([#31](https://github.com/groundsgg/plugin-proxy/issues/31)) ([8889a04](https://github.com/groundsgg/plugin-proxy/commit/8889a04abe8b374424a6bcb44e84757707b861d9))

## [1.0.0](https://github.com/groundsgg/plugin-proxy/compare/v0.8.0...v1.0.0) (2026-08-02)


### ⚠ BREAKING CHANGES

* **proxy:** the proxy.* subjects and their payload format change. A proxy on the previous version neither receives nor is reachable by one on this.

### Code Refactoring

* **proxy:** address cross-proxy messages by proxy, not by player ([#29](https://github.com/groundsgg/plugin-proxy/issues/29)) ([0b56dc3](https://github.com/groundsgg/plugin-proxy/commit/0b56dc3dbb27d86f1011605a17cddb70ec453e87))

## [0.8.0](https://github.com/groundsgg/plugin-proxy/compare/v0.7.0...v0.8.0) (2026-08-02)


### Features

* **lobby:** answer a backend asking for network player counts ([#27](https://github.com/groundsgg/plugin-proxy/issues/27)) ([8092587](https://github.com/groundsgg/plugin-proxy/commit/8092587cf24c62ffc62f9eff6637902c640e8906))

## [0.7.0](https://github.com/groundsgg/plugin-proxy/compare/v0.6.0...v0.7.0) (2026-08-02)


### Features

* **proxy:** add a global /motd, stored in service-config ([#25](https://github.com/groundsgg/plugin-proxy/issues/25)) ([8100aee](https://github.com/groundsgg/plugin-proxy/commit/8100aeefa5c79ad5c78948a89364ffababcb806d))

## [0.6.0](https://github.com/groundsgg/plugin-proxy/compare/v0.5.0...v0.6.0) (2026-08-02)


### Features

* **proxy:** put the network on the tab list ([#23](https://github.com/groundsgg/plugin-proxy/issues/23)) ([443f013](https://github.com/groundsgg/plugin-proxy/commit/443f013155552b6ade29ddeb676d7113a707a9b9))

## [0.5.0](https://github.com/groundsgg/plugin-proxy/compare/v0.4.0...v0.5.0) (2026-07-30)


### Features

* **api:** add PlayerLocaleQuery for cross-plugin language lookup ([#21](https://github.com/groundsgg/plugin-proxy/issues/21)) ([d3bbdf8](https://github.com/groundsgg/plugin-proxy/commit/d3bbdf8964f54976836abbe826a908d8e5a70418))

## [0.4.0](https://github.com/groundsgg/plugin-proxy/compare/v0.3.0...v0.4.0) (2026-07-24)


### Features

* **proxy:** show the network player count in the server list ([#19](https://github.com/groundsgg/plugin-proxy/issues/19)) ([9b2cc3f](https://github.com/groundsgg/plugin-proxy/commit/9b2cc3f07261d56da79be83f7a8c31ff66a94b38))

## [0.3.0](https://github.com/groundsgg/plugin-proxy/compare/v0.2.0...v0.3.0) (2026-07-24)


### Features

* **proxy:** move players between proxies, and report who is online where ([#17](https://github.com/groundsgg/plugin-proxy/issues/17)) ([7d56034](https://github.com/groundsgg/plugin-proxy/commit/7d5603490c3befe9e3103b373df49e0f4e387850))

## [0.2.0](https://github.com/groundsgg/plugin-proxy/compare/v0.1.0...v0.2.0) (2026-07-24)


### Features

* **proxy:** expose network-wide player counts per backend server ([#15](https://github.com/groundsgg/plugin-proxy/issues/15)) ([a2ccb9f](https://github.com/groundsgg/plugin-proxy/commit/a2ccb9f30a11be87b155a5f627b0c38b9e99bb8e))
