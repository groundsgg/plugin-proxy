package gg.grounds.proxy.velocity.welcome

import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.player.CookieReceiveEvent
import com.velocitypowered.api.event.player.PlayerChannelRegisterEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.network.HandshakeIntent
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.scheduler.ScheduledTask
import gg.grounds.i18n.Translations
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.Optional
import java.util.UUID
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WelcomeListenerTest {
    private val pending = mutableListOf<Runnable>()
    private var cancelled = 0
    private var ready = false
    private val messages =
        Translations.forBundle("gg.grounds.proxy.messages", javaClass.classLoader)
    private val listener = newListener()

    private fun newListener() =
        WelcomeListener(
            messages,
            WelcomeMessage(messages),
            schedule = { work ->
                pending += work
                fake(ScheduledTask::class.java) { name, _ ->
                    when (name) {
                        "cancel" -> {
                            cancelled++
                            null
                        }
                        else -> null
                    }
                }
            },
            logoReady = { ready },
        )

    private class Client(
        val player: Player,
        val received: MutableList<Component>,
        val cookies: MutableMap<Key, ByteArray>,
        val requested: MutableList<Key>,
    )

    private fun client(
        intent: HandshakeIntent = HandshakeIntent.LOGIN,
        id: UUID = UUID.randomUUID(),
        cookies: MutableMap<Key, ByteArray> = mutableMapOf(),
    ): Client {
        val received = mutableListOf<Component>()
        val requested = mutableListOf<Key>()
        val player =
            fake(Player::class.java) { name, args ->
                when (name) {
                    "getUniqueId" -> id
                    "getUsername" -> "Alex"
                    "getHandshakeIntent" -> intent
                    "getProtocolVersion" -> ProtocolVersion.MINECRAFT_1_21
                    "storeCookie" -> {
                        cookies[args!![0] as Key] = (args[1] as ByteArray).copyOf()
                        null
                    }
                    "requestCookie" -> {
                        requested += args!![0] as Key
                        null
                    }
                    "isActive" -> true
                    "get" -> Optional.of(Locale.ENGLISH)
                    "sendMessage" -> {
                        received += args!![0] as Component
                        null
                    }
                    else -> null
                }
            }
        return Client(player, received, cookies, requested)
    }

    private fun plain(client: Client) =
        client.received.joinToString { PlainTextComponentSerializer.plainText().serialize(it) }

    private fun connect(client: Client) =
        listener.onServerConnected(ServerPostConnectEvent(client.player, null))

    @Test
    fun `backend reconnect events cannot schedule a second welcome`() {
        val client = client()
        connect(client)
        connect(client)
        assertEquals(1, pending.size)
        pending.single().run()
        connect(client)
        assertEquals(1, client.received.size)
        assertEquals(1, pending.size)
        assertTrue(plain(client).contains("install"))
    }

    private val cookieKey = Key.key("grounds:welcome")

    @Test
    fun `transfers with delivered cookie onto either proxy do not repeat the welcome`() {
        for (proxy in listOf(listener, newListener())) {
            val client = client(HandshakeIntent.TRANSFER)
            proxy.onLogin(PostLoginEvent(client.player))
            proxy.onServerConnected(ServerPostConnectEvent(client.player, null))
            assertEquals(listOf(cookieKey), client.requested)
            assertTrue(pending.isEmpty())
            proxy.onCookie(CookieReceiveEvent(client.player, cookieKey, byteArrayOf(1)))
            assertTrue(pending.isEmpty())
        }
    }

    @Test
    fun `preferred region redirect before any backend receives its first welcome at destination`() {
        val cookies = mutableMapOf(cookieKey to byteArrayOf(1))
        val source = client(cookies = cookies)
        listener.onLogin(PostLoginEvent(source.player))
        listener.onDisconnect(
            DisconnectEvent(source.player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN)
        )
        val destination = client(HandshakeIntent.TRANSFER, cookies = cookies)
        val otherProxy = newListener()
        otherProxy.onLogin(PostLoginEvent(destination.player))
        otherProxy.onCookie(CookieReceiveEvent(destination.player, cookieKey, cookies[cookieKey]))
        otherProxy.onChannels(
            PlayerChannelRegisterEvent(
                destination.player,
                listOf(MinecraftChannelIdentifier.from("voicechat:secret")),
            )
        )
        otherProxy.onServerConnected(ServerPostConnectEvent(destination.player, null))
        assertEquals(1, pending.size)
        pending.single().run()
        assertEquals(1, destination.received.size)
        assertFalse(plain(destination).contains("install"))
        assertArrayEquals(byteArrayOf(1), cookies[cookieKey])
    }

    @Test
    fun `transfer during the delivery delay moves the pending welcome to the destination`() {
        val cookies = mutableMapOf<Key, ByteArray>()
        val source = client(cookies = cookies)
        listener.onLogin(PostLoginEvent(source.player))
        connect(source)
        listener.onDisconnect(
            DisconnectEvent(source.player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN)
        )
        val destination = client(HandshakeIntent.TRANSFER, cookies = cookies)
        val otherProxy = newListener()
        otherProxy.onLogin(PostLoginEvent(destination.player))
        otherProxy.onServerConnected(ServerPostConnectEvent(destination.player, null))
        otherProxy.onCookie(CookieReceiveEvent(destination.player, cookieKey, cookies[cookieKey]))
        assertEquals(2, pending.size)
        pending.forEach(Runnable::run)
        assertTrue(source.received.isEmpty())
        assertEquals(1, destination.received.size)
    }

    @Test
    fun `absent transfer cookie allows welcome but stale responses cannot reach a reconnect`() {
        val id = UUID.randomUUID()
        val old = client(HandshakeIntent.TRANSFER, id)
        listener.onLogin(PostLoginEvent(old.player))
        listener.onDisconnect(
            DisconnectEvent(old.player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN)
        )
        val current = client(HandshakeIntent.TRANSFER, id)
        listener.onLogin(PostLoginEvent(current.player))
        connect(current)
        listener.onCookie(CookieReceiveEvent(old.player, cookieKey, null))
        assertTrue(pending.isEmpty())
        val response = CookieReceiveEvent(current.player, cookieKey, null)
        listener.onCookie(response)
        assertFalse(response.result.isAllowed)
        assertEquals(1, pending.size)
        pending.single().run()
        assertEquals(1, current.received.size)
    }

    @Test
    fun `a backend switch never initiates a welcome`() {
        val client = client()
        val previous = fake(RegisteredServer::class.java) { _, _ -> null }
        listener.onServerConnected(ServerPostConnectEvent(client.player, previous))
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `client mod registration before backend connection hides installation help`() {
        val client = client()
        listener.onChannels(
            PlayerChannelRegisterEvent(
                client.player,
                listOf(MinecraftChannelIdentifier.from("voicechat:secret")),
            )
        )
        connect(client)
        pending.single().run()
        assertEquals(1, client.received.size)
        assertFalse(plain(client).contains("install"))
    }

    @Test
    fun `client handshake hides help even without connected voice and is forwarded intact`() {
        val client = client()
        connect(client)
        val backend = fake(ServerConnection::class.java) { _, _ -> null }
        val event =
            PluginMessageEvent(
                client.player,
                backend,
                MinecraftChannelIdentifier.from("voicechat:request_secret"),
                byteArrayOf(0, 0, 0, 20),
            )
        listener.onPluginMessage(event)
        assertTrue(event.result.isAllowed)
        pending.single().run()
        assertFalse(plain(client).contains("install"))
    }

    @Test
    fun `backend messages do not claim the client installed a mod`() {
        val client = client()
        connect(client)
        val backend = fake(ServerConnection::class.java) { _, _ -> null }
        listener.onPluginMessage(
            PluginMessageEvent(
                backend,
                client.player,
                MinecraftChannelIdentifier.from("voicechat:request_secret"),
                byteArrayOf(0, 0, 0, 20),
            )
        )
        pending.single().run()
        assertTrue(plain(client).contains("install"))
    }

    @Test
    fun `unrelated channels and malformed voice packets cannot suppress help`() {
        val client = client()
        connect(client)
        val backend = fake(ServerConnection::class.java) { _, _ -> null }
        listener.onChannels(
            PlayerChannelRegisterEvent(
                client.player,
                listOf(MinecraftChannelIdentifier.from("other:secret")),
            )
        )
        listener.onPluginMessage(
            PluginMessageEvent(
                client.player,
                backend,
                MinecraftChannelIdentifier.from("voicechat:request_secret"),
                byteArrayOf(),
            )
        )
        listener.onPluginMessage(
            PluginMessageEvent(
                client.player,
                backend,
                MinecraftChannelIdentifier.from("other:secret"),
                byteArrayOf(0, 0, 0, 20),
            )
        )
        pending.single().run()
        pending.single().run()
        assertEquals(1, client.received.size)
        assertTrue(plain(client).contains("install"))
    }

    @Test
    fun `disconnect cancels delivery and a stale callback cannot reach a new connection`() {
        val id = UUID.randomUUID()
        val first = client(id = id)
        connect(first)
        listener.onDisconnect(
            DisconnectEvent(first.player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN)
        )
        val second = client(id = id)
        connect(second)
        pending[0].run()
        pending[1].run()
        assertEquals(1, cancelled)
        assertTrue(first.received.isEmpty())
        assertEquals(1, second.received.size)
    }

    @Test
    fun `pack readiness is read when sending and shutdown cancels outstanding work`() {
        val first = client()
        connect(first)
        ready = true
        pending[0].run()
        assertTrue(plain(first).contains('\uE000'))
        val second = client()
        connect(second)
        listener.close()
        pending[1].run()
        assertTrue(second.received.isEmpty())
        assertTrue(cancelled >= 1)
    }

    private fun <T> fake(type: Class<T>, invoke: (String, Array<out Any?>?) -> Any?): T =
        type.cast(
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { proxy, method, args ->
                when (method.name) {
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.get(0)
                    "toString" -> type.simpleName
                    else -> invoke(method.name, args)
                }
            }
        )
}
