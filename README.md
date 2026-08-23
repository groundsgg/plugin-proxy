# plugin-proxy

The Velocity plugin that lets other plugins act on a player who is connected to a **different proxy**.

A Velocity proxy only knows its own players. Ask it for `dahendriik` while they are on the other proxy and you get nothing — which is why `/msg`, party invites and party warps used to answer "not online" the moment a second proxy existed. plugin-proxy is the piece that answers for the whole network.

## What it provides

`ProxyService`, published into the [`ProxyServiceRegistry`](api/src/main/kotlin/gg/grounds/proxy/api/ProxyServiceRegistry.kt):

| method                       | local player           | player on another proxy               |
| ---------------------------- | ---------------------- | ------------------------------------- |
| `resolvePlayerId(name)`      | Velocity's player list | `PlayerSessionQuery` (service-player) |
| `resolvePlayerName(id)`      | Velocity's player list | `PlayerSessionQuery`                  |
| `isOnline(id)`               | Velocity's player list | `PlayerSessionQuery`                  |
| `getPresence(id)`            | current server         | session's proxy + server              |
| `sendToPlayer(id, msg)`      | `player.sendMessage`   | publish `proxy.system.<id>`           |
| `transferPlayer(id, server)` | connection request     | publish `proxy.transfer.<id>`         |
| `suggestPlayerNames(prefix)` | filtered in memory     | prefix search, capped, 2s cache       |

Local first, always — a player on this proxy is already in memory and costs nothing to find.

## How the pieces fit

```
plugin-chat / plugin-social          consumers: only ever call ProxyService
        │
        ▼
   ProxyService            ← registered by plugin-proxy (this repo)
        │
        ├── lookups ──►  PlayerSessionQuery   ← registered by plugin-player,
        │                                        answered by service-player (Postgres, TTL)
        │
        └── delivery ──►  NATS: proxy.system.<uuid>, proxy.transfer.<uuid>
                             ↑ every proxy subscribes for its own players
```

Nobody registers `PlayerSessionQuery` → lookups return null and everything degrades to local-only, silently. That is exactly the state this repo was in before plugin-player shipped: the fallback existed and nothing filled it.

**Why service-player and not a registry in the proxies?** Presence already lives there — `TryPlayerLogin` is on the login path, with heartbeats and a TTL. A second store in the proxies would be a second source of truth with its own expiry, and two of those drift. An earlier attempt (plugin-chat's own `chat.players.join/leave` map) also had no memory: core NATS does not replay, so a proxy only ever learned about players who joined _while it was running_.

## Consuming it

```kotlin
// build.gradle.kts — compileOnly, NEVER shaded (see ProxyServiceRegistry's KDoc)
compileOnly("gg.grounds:plugin-proxy-api:0.1.0")
```

```kotlin
@Plugin(
    id = "plugin-yours",
    dependencies = [Dependency(id = "plugin-proxy")],   // orders init; optional = true to degrade
)
class YourPlugin {
    @Subscribe
    fun onInit(event: ProxyInitializeEvent) {
        val proxyService = ProxyServiceRegistry.get(ProxyService::class.java)
        val id = proxyService?.resolvePlayerId("dahendriik")   // null → plugin-proxy absent
    }
}
```

Shading `plugin-proxy-api` gives your plugin its own copy of the registry class — a different map, which nobody writes into. Every lookup then returns null and cross-proxy features degrade to local-only without a single error in the log. `compileOnly` is not a style preference here.

## Tab-complete does not list the network

`suggestPlayerNames(prefix, limit)` is a prefix search with a cap, and there is deliberately **no** "give me every online player". Velocity fires tab-complete on _every keystroke_: at 10k players online, a roster dump is a ~200 KB response issued thousands of times a second, with a table scan behind each one. So: local matches from memory, the network only once the prefix is ≥ 2 characters, answers cached 2s per prefix, result capped (default 20).

## The network MOTD

`/motd` changes what the whole network shows in the server list. The MOTD is stored in
**service-config** as one document (`app=velocity`, `namespace=motd`, `key=active`), so it survives
restarts, applies to every region including ones that come up later, and is the same document a
dashboard will edit later — nothing here is only true in-game.

```
/motd                        show what is being served, with the placeholders resolved
/motd set <MiniMessage>      change it; <newline> starts the second line
/motd import <link|id>       take a design from motd.gg
/motd maxplayers <n|clear>   the number right of the slash
/motd preview [MiniMessage]  render it to yourself, change nothing
/motd reset                  remove it; Velocity's own MOTD is served again
```

Gated on `grounds.motd.manage`, which is **not** one of the roles forge grants by default — grant it
to a custom role, the same way in-game administration is granted.

### Placeholders

Resolved per ping, so one stored MOTD reads differently depending on which region answered.

| token                            | value                                           |
| -------------------------------- | ----------------------------------------------- |
| `{{region}}`                     | `REGION` — the datacentre (`nl-ams1`)           |
| `{{localzone}}`, `{{continent}}` | `CONTINENT` — `eu` / `na`                       |
| `{{players}}`                    | the network-wide player count this ping reports |
| `{{max}}`                        | the player cap this ping reports                |

A known token with no value renders as nothing; an _unknown_ one is left standing, so a typo shows
up in the server list instead of disappearing.

### motd.gg import

[motd.gg](https://motd.gg) is a MOTD editor with a live server-list preview. Design there, then
`/motd import https://motd.gg/<id>` — the id, the link and the `.json` all work. Only the read half
of motd.gg's own plugin is mirrored: nothing is uploaded, so importing does not publish this
network's MOTD anywhere. A server icon in the imported document is reported and **not** applied —
the network icon is brand, served from the CDN.

### How it propagates

Every proxy polls `GetSnapshot`-style reads every `MOTD_REFRESH_SECONDS`; service-config's own
contract makes the read the source of truth and its NATS event only a latency hint, so subscribing
as well would buy seconds in exchange for a second way for the two to disagree. Whoever runs
`/motd set` sees it immediately on their own proxy; the rest follow within one interval. A refresh
that fails keeps the previous MOTD rather than emptying every region's server-list entry at once.

Writes go to `ConfigAdminService`, which service-config restricts to admin service accounts and to
writers explicitly allowed for the app — see `GROUNDS_CONFIG_WRITERS` there. A proxy that is not
allowed can still _show_ the MOTD; `/motd set` then reports the refusal instead of failing silently.

## Configuration

| env                    | meaning                                                                                                                                   |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `NATS_URL`             | broker for `proxy.system.*` / `proxy.transfer.*` (default `nats://nats.infra:4222`)                                                       |
| `PROXY_ID`             | this proxy's identity, recorded in a player's session — must differ per proxy (`velocity`, `velocity-2`)                                  |
| `GROUNDS_TOKEN_FILE`   | projected SA-token, presented as the NATS bearer and as the service-config gRPC bearer (default `/var/run/secrets/grounds/token`)         |
| `CONFIG_SERVICE_URL`   | service-config contract target, e.g. `service-config:9000`. **Unset disables `/motd` entirely** and Velocity's own MOTD is served         |
| `CONFIG_GRPC_TARGET`   | Legacy fallback for deployments that have not migrated to `CONFIG_SERVICE_URL`                                                            |
| `CONFIG_ENV`           | which environment's document to use; falls back to `GROUNDS_PERMISSION_ENVIRONMENT`                                                       |
| `CONFIG_APP`           | which service-config app holds it (default `velocity` — deliberately not the release name, so `velocity` and `velocity-2` share one MOTD) |
| `MOTD_REFRESH_SECONDS` | how often each proxy re-reads it (default `15`)                                                                                           |
| `REGION`               | `{{region}}`, and the region `/region` considers "here"                                                                                   |
| `CONTINENT`            | `{{localzone}}` / `{{continent}}`                                                                                                         |

### Bedrock device platforms

On a proxy that also runs Floodgate — only `velocity-bedrock` does — the endpoint additionally
publishes which platform Bedrock players are on:

```text
velocity_bedrock_players{device_os="ANDROID"}   Floodgate's DeviceOs, by enum name:
                                                ANDROID, IOS, OSX, XBOX, NX, PS4, UWP, …
```

Nothing else in the estate can answer this. The device travels in a Bedrock client's login chain,
Geyser hands it to Floodgate, and by the time Velocity sees the player they are an ordinary
Java-protocol connection with the platform stripped off — the game servers cannot tell a Switch
from a phone.

Floodgate is read **reflectively** and is not a build dependency: this plugin loads on every proxy
and only one of them has Floodgate, and GeyserMC publishes the API as a SNAPSHOT only. A proxy
without Floodgate publishes no `velocity_bedrock_players` series at all — absent rather than zero,
because "no Bedrock players" and "cannot see Bedrock players" are different states.

A platform that empties keeps its series and reports 0, so a graph shows nobody on a Switch rather
than a gap.

The NATS auth-callout scopes each pod to the subjects declared in its bundle `events:` block, so `proxy.system.*` and `proxy.transfer.*` must be listed there — an undeclared subject is denied and the message vanishes.

## Build

```bash
./gradlew build -Pgithub.user="$GITHUB_ACTOR" -Pgithub.token="$GITHUB_TOKEN"
```

Published on tag as `gg.grounds:plugin-proxy-api` (consume this) and `plugin-proxy-velocity`, and as `ghcr.io/groundsgg/plugin-proxy` — the image carries the shaded JAR at `/jar/plugin.jar` for the `plugin-velocity-jar` Helm chart.
