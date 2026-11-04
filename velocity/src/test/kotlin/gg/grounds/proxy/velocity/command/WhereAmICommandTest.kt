package gg.grounds.proxy.velocity.command

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.server.ServerInfo
import gg.grounds.i18n.Translations
import gg.grounds.presentation.VelocitySystemMessages
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.util.Locale
import java.util.Optional
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhereAmICommandTest {
    private val messages =
        Translations.forBundleWithSystemRenderer(
            "gg.grounds.proxy.messages",
            javaClass.classLoader,
            systemRenderer = VelocitySystemMessages.renderer,
        )
    private val received = mutableListOf<Component>()
    private var server: String? = "lobby-1"

    @Test
    fun `each proxy reports its own region and proxy without a permission grant`() {
        for ((region, proxyId) in
            listOf("nl-ams1" to "velocity-eu-1", "us-nyc1" to "velocity-na-2")) {
            received.clear()
            val command = WhereAmICommand({ region }, { proxyId }, messages)
            val invocation = invocation(player())

            assertTrue(command.hasPermission(invocation))
            command.execute(invocation)

            assertEquals(1, received.size)
            assertTrue(plain().contains("Region: $region"), plain())
            assertTrue(plain().contains("Proxy: $proxyId"), plain())
            assertTrue(plain().contains("Server: lobby-1"), plain())
        }
    }

    @Test
    fun `support block has one prefix and indented rows with copyable full IDs`() {
        val proxyId = "velocity-eu-5fbc57c96d-r8pf9"
        server = "minestom-lobby-7c77bc4b6f-9zpht"
        WhereAmICommand({ "nl-ams1" }, { proxyId }, messages, iconsReady = { true })
            .execute(invocation(player(Locale.GERMAN)))

        val lines = plain().lines()
        assertEquals(6, lines.size)
        assertTrue(lines[0].contains("Dein Standort im Netzwerk"), plain())
        assertTrue(lines[1].startsWith("  "), plain())
        assertTrue(lines[1].contains("Support"), plain())
        assertEquals("", lines[2])
        assertEquals("  \uE410 Region: nl-ams1", lines[3])
        assertEquals("  \uE411 Proxy: $proxyId", lines[4])
        assertEquals("  \uE412 Server: $server", lines[5])
        val copies = nodes(received.single()).filter { it.clickEvent() != null }
        assertEquals(2, copies.size)
        assertEquals(ClickEvent.copyToClipboard(proxyId), copies[0].clickEvent())
        assertEquals(ClickEvent.copyToClipboard(server!!), copies[1].clickEvent())
        copies.forEach { node ->
            val hint = node.hoverEvent()!!.value() as Component
            assertTrue(
                PlainTextComponentSerializer.plainText()
                    .serialize(hint)
                    .contains("kopieren", ignoreCase = true)
            )
        }
    }

    @Test
    fun `location block follows Minecraft Mini CD instead of web palette colors`() {
        WhereAmICommand({ "nl-ams1" }, { "velocity-eu-1" }, messages, iconsReady = { true })
            .execute(invocation(player(Locale.GERMAN)))

        val content = nodes(received.single()).filterIsInstance<TextComponent>()
        val title = content.single { it.content() == "Dein Standort im Netzwerk" }
        assertEquals(TextDecoration.State.TRUE, title.decoration(TextDecoration.BOLD))
        val region = content.single { it.content() == "nl-ams1" }
        assertEquals(TextColor.color(0xF3F0EA), region.color())
        val copies = content.filter { it.clickEvent() != null }
        assertEquals(2, copies.size)
        copies.forEach { assertEquals(TextColor.color(0x67D9EC), it.color()) }
        val icons = content.filter { it.content() in listOf("\uE410", "\uE411", "\uE412") }
        assertEquals(3, icons.size)
        icons.forEach {
            assertEquals(TextColor.color(0xFFFFFF), it.color())
            assertEquals("grounds:connection", it.font()?.asString())
        }
        val hint = content.single { it.content().contains("Support") }
        assertEquals(TextColor.color(0xC1B9AA), hint.color())
    }

    @Test
    fun `a backend switch is reflected on the next invocation`() {
        val command = WhereAmICommand({ "nl-ams1" }, { "velocity-eu-1" }, messages)
        val invocation = invocation(player())
        command.execute(invocation)
        received.clear()

        server = "bedwars-9"
        command.execute(invocation)

        assertTrue(plain().contains("Server: bedwars-9"), plain())
        assertFalse(plain().contains("lobby-1"), plain())
    }

    @Test
    fun `missing region proxy and backend render as unknown`() {
        server = null
        WhereAmICommand({ null }, { "  " }, messages).execute(invocation(player()))

        assertTrue(plain().contains("Region: —"), plain())
        assertTrue(plain().contains("Proxy: —"), plain())
        assertTrue(plain().contains("Server: —"), plain())
        assertTrue(nodes(received.single()).all { it.clickEvent() == null })
    }

    @Test
    fun `console gets a localized player-only message`() {
        val console = source(CommandSource::class.java, Locale.GERMAN)
        WhereAmICommand({ error("Console has no location") }, { "proxy" }, messages)
            .execute(invocation(console))

        assertEquals(1, received.size)
        assertTrue(plain().contains("nur im Spiel"), plain())
    }

    @Test
    fun `location values render safely in every supported locale`() {
        for (locale in
            listOf(Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH, Locale.forLanguageTag("es"))) {
            received.clear()
            WhereAmICommand({ "nl-ams1" }, { "<red>proxy</red>" }, messages)
                .execute(invocation(player(locale)))

            assertEquals(1, received.size)
            assertTrue(plain().contains("nl-ams1"), plain())
            assertTrue(plain().contains("<red>proxy</red>"), plain())
            assertTrue(plain().contains("lobby-1"), plain())
            assertFalse(plain().contains("whereami."), plain())
        }
    }

    @Test
    fun `unverified clients retain text labels without private glyphs`() {
        WhereAmICommand({ "nl-ams1" }, { "velocity-eu-1" }, messages).execute(invocation(player()))
        assertFalse(plain().any { it in '\uE000'..'\uF8FF' })
        assertTrue(plain().lines().contains("  Region: nl-ams1"), plain())
    }

    @Test
    fun `connection glyphs require every allowed HUD pack to contain them`() {
        assertFalse(WhereAmIGlyphs.enabled(emptyMap()))
        assertFalse(WhereAmIGlyphs.enabled(mapOf("HUD_FONT_PACK_SHA1S" to "old")))
        assertFalse(
            WhereAmIGlyphs.enabled(
                mapOf("HUD_FONT_PACK_SHA1S" to "old,new", "WHEREAMI_FONT_PACK_SHA1S" to "new")
            )
        )
        assertTrue(
            WhereAmIGlyphs.enabled(
                mapOf("HUD_FONT_PACK_SHA1S" to " new ", "WHEREAMI_FONT_PACK_SHA1S" to "new")
            )
        )
    }

    private fun nodes(component: Component): List<Component> =
        listOf(component) + component.children().flatMap(::nodes)

    private fun plain() =
        received.joinToString("\n") { PlainTextComponentSerializer.plainText().serialize(it) }

    private fun player(locale: Locale = Locale.ENGLISH): Player = source(Player::class.java, locale)

    private fun <T> source(type: Class<T>, locale: Locale): T =
        fake(type) { name, args ->
            when (name) {
                "getCurrentServer" ->
                    Optional.ofNullable(
                        server?.let { name ->
                            fake(ServerConnection::class.java) { method, _ ->
                                if (method == "getServerInfo") {
                                    ServerInfo(name, InetSocketAddress("127.0.0.1", 25565))
                                } else error("Unexpected server method: $method")
                            }
                        }
                    )
                "get" -> Optional.of(locale)
                "hasPermission" -> false
                "sendMessage" -> {
                    received += args!!.filterIsInstance<Component>().single()
                    null
                }
                else -> error("Unexpected source method: $name")
            }
        }

    private fun invocation(source: CommandSource): SimpleCommand.Invocation =
        fake(SimpleCommand.Invocation::class.java) { name, _ ->
            when (name) {
                "source" -> source
                "arguments" -> emptyArray<String>()
                "alias" -> "whereami"
                else -> error("Unexpected invocation method: $name")
            }
        }

    private fun <T> fake(type: Class<T>, invoke: (String, Array<out Any?>?) -> Any?): T =
        type.cast(
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, args ->
                invoke(method.name, args)
            }
        )
}
