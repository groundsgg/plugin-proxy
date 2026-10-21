package gg.grounds.proxy.velocity.motd

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.proxy.server.ServerPing
import gg.grounds.proxy.velocity.maintenance.MaintenanceEngine
import java.time.Instant

/**
 * Production handler; test instances inject independent cached stores and shared network presence.
 */
class ServerListPingListener(
    private val manager: () -> MotdManager?,
    private val networkPlayers: () -> Int?,
    private val maintenance: () -> MaintenanceEngine?,
    private val region: () -> String?,
    private val continent: () -> String?,
    private val now: () -> Instant = Instant::now,
) {
    @Subscribe
    fun onProxyPing(event: ProxyPingEvent) {
        val sampledNow = now()
        val normal = manager()?.current()
        val engine = maintenance()
        val active = engine?.serverListDocument(sampledNow)
        val base = event.ping.asBuilder()
        normal?.maxPlayers?.let(base::maximumPlayers)
        var template = normal?.serverList
        val override = active?.serverList
        var fallback = normal?.text
        var compiled = normal?.compiledFallback
        var compiledMotd =
            normal?.serverList?.motd?.takeIf { normal.serverListFallbackText == normal.text }
        if (active != null) {
            if (active.motd.isNotBlank()) {
                fallback = engine.motd(sampledNow)
                compiled = null
                compiledMotd =
                    override?.motd?.takeIf { active.serverListFallbackText == active.motd }
            }
            if (override != null) {
                val fields =
                    active.serverListOverrides ?: setOf("version", "icon", "players", "hover")
                val inherited =
                    template
                        ?: override.copy(
                            versionMode = "automatic",
                            versionText = null,
                            icon = null,
                            hidden = false,
                            online = ServerCountRule("inherit"),
                            maximum = ServerCountRule("inherit"),
                            hoverMode = "inherit",
                            hoverText = null,
                        )
                template =
                    inherited.copy(
                        versionMode =
                            if ("version" in fields) override.versionMode
                            else inherited.versionMode,
                        versionText =
                            if ("version" in fields) override.versionText
                            else inherited.versionText,
                        icon = if ("icon" in fields) override.icon else inherited.icon,
                        hidden = if ("players" in fields) override.hidden else inherited.hidden,
                        online =
                            if ("players" in fields && override.online.mode != "inherit")
                                override.online
                            else inherited.online,
                        maximum =
                            if ("players" in fields && override.maximum.mode != "inherit")
                                override.maximum
                            else inherited.maximum,
                        hoverMode =
                            if ("hover" in fields) override.hoverMode else inherited.hoverMode,
                        hoverText =
                            if ("hover" in fields) override.hoverText else inherited.hoverText,
                    )
            }
        }
        event.ping =
            ServerListEvaluator.evaluate(
                base.build(),
                template,
                networkPlayers(),
                event.connection.protocolVersion.protocol,
                fallback,
                region(),
                continent(),
                compiled,
                compiledMotd,
                if (active != null) engine.serverListValues(sampledNow) else emptyMap(),
            )
        // Preserve the legacy maintenance marker only for documents without a status template.
        if (active != null && override == null) {
            engine.protocolName(sampledNow)?.let {
                event.ping = event.ping.asBuilder().version(ServerPing.Version(-1, it)).build()
            }
        }
    }
}
