package gg.grounds.proxy.velocity.motd

import com.velocitypowered.api.proxy.server.ServerPing
import java.util.UUID
import net.kyori.adventure.text.minimessage.MiniMessage

/** Only cached data is consumed here; no configuration or presence requests on the ping path. */
object ServerListEvaluator {
    fun evaluate(
        base: ServerPing,
        template: ServerListTemplate?,
        networkPlayers: Int?,
        clientProtocol: Int,
        fallbackText: String?,
        region: String?,
        continent: String?,
        compiledFallback: CompiledServerText? = null,
        compiledMotd: CompiledMotd? = null,
        runtimeValues: Map<String, String> = emptyMap(),
    ): ServerPing {
        val builder = base.asBuilder()
        networkPlayers?.let { builder.onlinePlayers(it) }
        template?.let {
            val online = it.online.apply(builder.onlinePlayers)
            val maximum = it.maximum.apply(builder.maximumPlayers, online)
            builder.onlinePlayers(online).maximumPlayers(maximum)
        }
        val players = builder.onlinePlayers
        val max = builder.maximumPlayers
        val values =
            runtimeValues +
                mapOf(
                    "players" to players.toString(),
                    "max" to max.toString(),
                    "region" to region.orEmpty(),
                    "continent" to continent.orEmpty(),
                    "localzone" to continent.orEmpty(),
                )
        if (compiledMotd != null)
            builder.description(
                compiledMotd.render(
                    values,
                    if (clientProtocol in 774..777) 267 else 271,
                    clientProtocol >= 773,
                )
            )
        else if (compiledFallback != null)
            builder.description(
                projectNativeSprites(compiledFallback.render(values), clientProtocol >= 773)
            )
        else
            fallbackText?.let {
                builder.description(
                    projectNativeSprites(
                        MiniMessage.miniMessage()
                            .deserialize(
                                MotdPlaceholders.render(
                                    it,
                                    MotdPlaceholders.Context(region, continent, players, max),
                                )
                            ),
                        clientProtocol >= 773,
                    )
                )
            }
        template?.let {
            val incompatible = base.version.protocol != clientProtocol
            if (it.versionMode == "always" || it.versionMode == "when-incompatible" && incompatible)
                builder.version(
                    ServerPing.Version(
                        if (clientProtocol == -1) -2 else -1,
                        it.versionText!!.legacy(values),
                    )
                )
            it.icon?.let(builder::favicon)
            when (it.hoverMode) {
                "disabled" -> builder.clearSamplePlayers()
                "custom" ->
                    builder
                        .clearSamplePlayers()
                        .samplePlayers(
                            it.hoverText!!.legacyLines(values).map { name ->
                                ServerPing.SamplePlayer(name, UUID(0, 0))
                            }
                        )
            }
            if (it.hidden) builder.nullPlayers()
        }
        return builder.build()
    }
}
