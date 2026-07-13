# plugin-proxy

The Velocity plugin that lets other plugins act on a player who is connected to a **different proxy**.

A Velocity proxy only knows its own players. Ask it for `dahendriik` while they are on the other proxy and you get nothing — which is why `/msg`, party invites and party warps used to answer "not online" the moment a second proxy existed. plugin-proxy is the piece that answers for the whole network.

## What it provides

`ProxyService`, published into the [`ProxyServiceRegistry`](api/src/main/kotlin/gg/grounds/proxy/api/ProxyServiceRegistry.kt):

| method | local player | player on another proxy |
|---|---|---|
| `resolvePlayerId(name)` | Velocity's player list | `PlayerSessionQuery` (service-player) |
| `resolvePlayerName(id)` | Velocity's player list | `PlayerSessionQuery` |
| `isOnline(id)` | Velocity's player list | `PlayerSessionQuery` |
| `getPresence(id)` | current server | session's proxy + server |
| `sendToPlayer(id, msg)` | `player.sendMessage` | publish `proxy.system.<id>` |
| `transferPlayer(id, server)` | connection request | publish `proxy.transfer.<id>` |
| `suggestPlayerNames(prefix)` | filtered in memory | prefix search, capped, 2s cache |

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

**Why service-player and not a registry in the proxies?** Presence already lives there — `TryPlayerLogin` is on the login path, with heartbeats and a TTL. A second store in the proxies would be a second source of truth with its own expiry, and two of those drift. An earlier attempt (plugin-chat's own `chat.players.join/leave` map) also had no memory: core NATS does not replay, so a proxy only ever learned about players who joined *while it was running*.

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

`suggestPlayerNames(prefix, limit)` is a prefix search with a cap, and there is deliberately **no** "give me every online player". Velocity fires tab-complete on *every keystroke*: at 10k players online, a roster dump is a ~200 KB response issued thousands of times a second, with a table scan behind each one. So: local matches from memory, the network only once the prefix is ≥ 2 characters, answers cached 2s per prefix, result capped (default 20).

## Configuration

| env | meaning |
|---|---|
| `NATS_URL` | broker for `proxy.system.*` / `proxy.transfer.*` (default `nats://nats.infra:4222`) |
| `PROXY_ID` | this proxy's identity, recorded in a player's session — must differ per proxy (`velocity`, `velocity-2`) |
| `GROUNDS_TOKEN_FILE` | projected SA-token presented as the NATS bearer (default `/var/run/secrets/grounds/token`) |

The NATS auth-callout scopes each pod to the subjects declared in its bundle `events:` block, so `proxy.system.*` and `proxy.transfer.*` must be listed there — an undeclared subject is denied and the message vanishes.

## Build

```bash
./gradlew build -Pgithub.user="$GITHUB_ACTOR" -Pgithub.token="$GITHUB_TOKEN"
```

Published on tag as `gg.grounds:plugin-proxy-api` (consume this) and `plugin-proxy-velocity`, and as `ghcr.io/groundsgg/plugin-proxy` — the image carries the shaded JAR at `/jar/plugin.jar` for the `plugin-velocity-jar` Helm chart.
