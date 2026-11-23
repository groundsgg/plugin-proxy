package gg.grounds.proxy.velocity

import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.util.ServerLink
import gg.grounds.proxy.velocity.welcome.WelcomeLinks
import java.lang.reflect.Proxy
import java.util.Optional
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class ServerLinksTest {
    @Test
    fun `each proxy sends the Grounds links on join and backend switch`() {
        val previousServer = fake<RegisteredServer> { method, _ -> error(method) }

        repeat(2) {
            val sent = mutableListOf<ServerLink>()
            val player = player(ProtocolVersion.MINECRAFT_1_21, sent)
            val proxy = plugin()
            proxy.onServerConnected(ServerPostConnectEvent(player, null))
            proxy.onServerConnected(ServerPostConnectEvent(player, previousServer))

            val urls = listOf("https://grounds.gg", WelcomeLinks.from().discord.trim())
            assertEquals(urls + urls, sent.map { it.url.toString() })
            assertEquals(ServerLink.Type.WEBSITE, sent[0].builtInType.orElseThrow())
            assertEquals(Component.text("Discord"), sent[1].customLabel.orElseThrow())
        }
    }

    @Test
    fun `clients before Minecraft 1_21 receive no links`() {
        val sent = mutableListOf<ServerLink>()
        val player = player(ProtocolVersion.MINECRAFT_1_20_5, sent)

        plugin().onServerConnected(ServerPostConnectEvent(player, null))

        assertEquals(emptyList<ServerLink>(), sent)
    }

    private fun plugin() =
        GroundsProxyPlugin(
            fake<ProxyServer> { method, _ -> error(method) },
            LoggerFactory.getLogger(ServerLinksTest::class.java),
        )

    private fun player(protocol: ProtocolVersion, sent: MutableList<ServerLink>): Player =
        fake { method, args ->
            when (method) {
                "getCurrentServer" -> Optional.of(fake<ServerConnection> { name, _ -> error(name) })
                "getProtocolVersion" -> protocol
                "setServerLinks" -> {
                    @Suppress("UNCHECKED_CAST") val links = args!!.single() as List<ServerLink>
                    sent.addAll(links)
                    null
                }
                else -> error(method)
            }
        }

    private inline fun <reified T> fake(crossinline handle: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args
            ->
            handle(method.name, args)
        } as T
}
