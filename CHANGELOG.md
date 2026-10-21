# Changelog

## [2.11.0](https://github.com/groundsgg/plugin-proxy/compare/v2.10.0...v2.11.0) (2026-10-21)


### Features

* **proxy:** support standalone server-list templates ([#104](https://github.com/groundsgg/plugin-proxy/issues/104)) ([0b9481f](https://github.com/groundsgg/plugin-proxy/commit/0b9481f9ddb65d3daa9049eb5bd871fea3fa8c72))


### Bug Fixes

* **proxy:** show maintenance in protocol and make MOTD optional ([#102](https://github.com/groundsgg/plugin-proxy/issues/102)) ([2e96dad](https://github.com/groundsgg/plugin-proxy/commit/2e96dad0d62f7a128aeeaab2c233fab8359b2501))

## [2.10.0](https://github.com/groundsgg/plugin-proxy/compare/v2.9.0...v2.10.0) (2026-10-19)


### Features

* **proxy:** enforce shared scheduled network maintenance ([#100](https://github.com/groundsgg/plugin-proxy/issues/100)) ([ffb606f](https://github.com/groundsgg/plugin-proxy/commit/ffb606f5d4806d4b7b92218a562d6ac032ac5be6))

## [2.9.0](https://github.com/groundsgg/plugin-proxy/compare/v2.8.0...v2.9.0) (2026-10-19)


### Features

* **proxy:** /proxy &lt;id&gt; to move to a specific proxy ([#90](https://github.com/groundsgg/plugin-proxy/issues/90)) ([79c3120](https://github.com/groundsgg/plugin-proxy/commit/79c3120c9c5be31159380a22a29a172b93b43b46))
* **proxy:** serve network server counts from the player-count broadcast ([#99](https://github.com/groundsgg/plugin-proxy/issues/99)) ([815d841](https://github.com/groundsgg/plugin-proxy/commit/815d841244438f47b17145ed316b7126f21b3b7c))
* **proxy:** tab-complete empty proxies in /proxy ([#97](https://github.com/groundsgg/plugin-proxy/issues/97)) ([bc58481](https://github.com/groundsgg/plugin-proxy/commit/bc58481bea5a79343710094f5c6f2552dfefbf1b))
* **tab:** sort by rank and name with a two-line footer ([#95](https://github.com/groundsgg/plugin-proxy/issues/95)) ([74e2e88](https://github.com/groundsgg/plugin-proxy/commit/74e2e885f045995c5a7dfe94e7bc1c4fa3e8934e))


### Bug Fixes

* **badges:** distinguish MOD from DEV with muted petrol ([#96](https://github.com/groundsgg/plugin-proxy/issues/96)) ([2b7bfe9](https://github.com/groundsgg/plugin-proxy/commit/2b7bfe9b73efe6ae4771814d776626143af75a9b))
* **tab:** add breathing room below the footer domain ([#98](https://github.com/groundsgg/plugin-proxy/issues/98)) ([955036b](https://github.com/groundsgg/plugin-proxy/commit/955036ba832bbeca80d34843bd323527a7fcd5da))

## [2.8.0](https://github.com/groundsgg/plugin-proxy/compare/v2.7.0...v2.8.0) (2026-10-17)


### Features

* **proxy:** display Bedrock identity and transfer profile sessions ([#91](https://github.com/groundsgg/plugin-proxy/issues/91)) ([341a555](https://github.com/groundsgg/plugin-proxy/commit/341a555fd83b15388644ee322e9a8f91e71592bc))


### Bug Fixes

* **badges:** add the moss-green MOD variant ([#87](https://github.com/groundsgg/plugin-proxy/issues/87)) ([ae2f64d](https://github.com/groundsgg/plugin-proxy/commit/ae2f64da0fc857278c82f3a2150fe2f0dc3c357f))
* **docker:** preserve release versions in permission manifests ([#92](https://github.com/groundsgg/plugin-proxy/issues/92)) ([1ede31c](https://github.com/groundsgg/plugin-proxy/commit/1ede31c95b1c5cb766c198977b755ebc63307cb4))
* **permissions:** index the proxy debug command permission ([#93](https://github.com/groundsgg/plugin-proxy/issues/93)) ([38fcf0f](https://github.com/groundsgg/plugin-proxy/commit/38fcf0f96f5c3453e17611c9ba46ee742923e7dc))
* **tab:** retain initial ranks while remote queues drain ([#89](https://github.com/groundsgg/plugin-proxy/issues/89)) ([c9c1415](https://github.com/groundsgg/plugin-proxy/commit/c9c1415bb85f6749d58accde8ee71e172c47bd86))

## [2.7.0](https://github.com/groundsgg/plugin-proxy/compare/v2.6.1...v2.7.0) (2026-10-17)


### Features

* **catalog:** publish versioned permission manifest ([#86](https://github.com/groundsgg/plugin-proxy/issues/86)) ([697da2a](https://github.com/groundsgg/plugin-proxy/commit/697da2ac9972ce290d44abbfd870eee7f3dbb3bf))


### Bug Fixes

* **chat:** align context and message-level colors ([#82](https://github.com/groundsgg/plugin-proxy/issues/82)) ([eb816bb](https://github.com/groundsgg/plugin-proxy/commit/eb816bb5d65e146f014847fc2b96f6b12ce56e27))
* **tab:** render native rank badges for Bedrock viewers ([#84](https://github.com/groundsgg/plugin-proxy/issues/84)) ([73a1266](https://github.com/groundsgg/plugin-proxy/commit/73a1266519a067f3fe986bba11f51e09e4fa06b8))
* **tab:** simplify footer and retain the initial rank badge ([#85](https://github.com/groundsgg/plugin-proxy/issues/85)) ([64a9624](https://github.com/groundsgg/plugin-proxy/commit/64a9624b0f3f2a415885634da453d580af6731a8))

## [2.6.1](https://github.com/groundsgg/plugin-proxy/compare/v2.6.0...v2.6.1) (2026-10-17)


### Bug Fixes

* **build:** resolve dependencies through the runner mirror in Docker builds ([#79](https://github.com/groundsgg/plugin-proxy/issues/79)) ([57b0355](https://github.com/groundsgg/plugin-proxy/commit/57b0355aa10a7b4fb1ebd07a248bb4f7d1acdd92))
* **chat:** use the shared system message layout ([#81](https://github.com/groundsgg/plugin-proxy/issues/81)) ([183f9ac](https://github.com/groundsgg/plugin-proxy/commit/183f9ac07848ec44396564efdad5d260732bfa9d))

## [2.6.0](https://github.com/groundsgg/plugin-proxy/compare/v2.5.0...v2.6.0) (2026-10-14)


### Features

* **chat:** add recipient rendering and appearance APIs ([#77](https://github.com/groundsgg/plugin-proxy/issues/77)) ([b408e15](https://github.com/groundsgg/plugin-proxy/commit/b408e15b024892d74050a922c8860f6659154d4f))

## [2.5.0](https://github.com/groundsgg/plugin-proxy/compare/v2.4.0...v2.5.0) (2026-10-14)


### Features

* **proxy:** hand a bare /region on a lobby to the lobby's world map ([#75](https://github.com/groundsgg/plugin-proxy/issues/75)) ([f762c69](https://github.com/groundsgg/plugin-proxy/commit/f762c697be06f3166e7e9111855e165b161a0836))

## [2.4.0](https://github.com/groundsgg/plugin-proxy/compare/v2.3.3...v2.4.0) (2026-10-14)


### Features

* **i18n:** add de/fr/es tab-list bundles checked by BundleCheck ([#72](https://github.com/groundsgg/plugin-proxy/issues/72)) ([dab0ced](https://github.com/groundsgg/plugin-proxy/commit/dab0ceddd27191b18fa26ce1452218e95cfdd845))

## [2.3.3](https://github.com/groundsgg/plugin-proxy/compare/v2.3.2...v2.3.3) (2026-10-12)


### Bug Fixes

* **region:** compare /region codes with CONTINENT, not REGION ([#69](https://github.com/groundsgg/plugin-proxy/issues/69)) ([d83e6c7](https://github.com/groundsgg/plugin-proxy/commit/d83e6c7fa8675f90506498ef2fa9b8e1eb8718c1))

## [2.3.2](https://github.com/groundsgg/plugin-proxy/compare/v2.3.1...v2.3.2) (2026-10-11)


### Bug Fixes

* **tab:** drop the language chip from tab list names ([#67](https://github.com/groundsgg/plugin-proxy/issues/67)) ([3eb1946](https://github.com/groundsgg/plugin-proxy/commit/3eb1946a5f78014f72bd867169d092a4c90dc97d))

## [2.3.1](https://github.com/groundsgg/plugin-proxy/compare/v2.3.0...v2.3.1) (2026-09-27)


### Bug Fixes

* **proxy:** expire cached network player count after 30s ([#62](https://github.com/groundsgg/plugin-proxy/issues/62)) ([ff21795](https://github.com/groundsgg/plugin-proxy/commit/ff21795ce74c10c3592d52ea84b80ff61b862940))

## [2.3.0](https://github.com/groundsgg/plugin-proxy/compare/v2.2.2...v2.3.0) (2026-09-02)


### Features

* compact tab ranks and mark Bedrock players ([#58](https://github.com/groundsgg/plugin-proxy/issues/58)) ([fb5b0df](https://github.com/groundsgg/plugin-proxy/commit/fb5b0df7e9b6593d0ece5716dd6ed41ef1f15102))

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
