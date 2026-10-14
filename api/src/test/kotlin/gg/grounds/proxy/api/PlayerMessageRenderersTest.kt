package gg.grounds.proxy.api

import java.util.UUID
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerMessageRenderersTest {
    @AfterEach
    fun clearRenderer() {
        ProxyServiceRegistry.unregister(PlayerMessageRenderer::class.java)
    }

    @Test
    fun `missing renderer preserves the exact rich component`() {
        val original = richMessage()

        assertSame(original, PlayerMessageRenderers.renderOrOriginal(PLAYER_ID, original))
    }

    @Test
    fun `runtime and linkage failures preserve the exact rich component`() {
        val original = richMessage()
        listOf<PlayerMessageRenderer>(
                PlayerMessageRenderer { _, _ -> throw IllegalStateException("unavailable") },
                PlayerMessageRenderer { _, _ -> throw NoClassDefFoundError("unavailable") },
            )
            .forEach { renderer ->
                ProxyServiceRegistry.register(PlayerMessageRenderer::class.java, renderer)
                assertSame(original, PlayerMessageRenderers.renderOrOriginal(PLAYER_ID, original))
            }
    }

    @Test
    fun `equal but distinct provider cannot withdraw the owner`() {
        class EqualRenderer : PlayerMessageRenderer {
            override fun render(playerId: UUID, message: Component) = message

            override fun equals(other: Any?) = other is EqualRenderer

            override fun hashCode() = 1
        }
        val owner = EqualRenderer()
        ProxyServiceRegistry.register(PlayerMessageRenderer::class.java, owner)
        assertFalse(
            ProxyServiceRegistry.unregister(PlayerMessageRenderer::class.java, EqualRenderer())
        )
        assertSame(owner, ProxyServiceRegistry.get(PlayerMessageRenderer::class.java))
    }

    @Test
    fun `provider registration and removal preserve ownership`() {
        val first = PlayerMessageRenderer { _, message -> message }
        val second = PlayerMessageRenderer { _, message -> message }

        assertTrue(ProxyServiceRegistry.registerIfAbsent(PlayerMessageRenderer::class.java, first))
        assertFalse(
            ProxyServiceRegistry.registerIfAbsent(PlayerMessageRenderer::class.java, second)
        )
        assertSame(first, ProxyServiceRegistry.get(PlayerMessageRenderer::class.java))
        assertFalse(ProxyServiceRegistry.unregister(PlayerMessageRenderer::class.java, second))
        assertSame(first, ProxyServiceRegistry.get(PlayerMessageRenderer::class.java))
        assertTrue(ProxyServiceRegistry.unregister(PlayerMessageRenderer::class.java, first))
    }

    private fun richMessage(): Component =
        Component.text("arbitrary")
            .clickEvent(ClickEvent.runCommand("/keep"))
            .hoverEvent(HoverEvent.showText(Component.text("still here")))

    companion object {
        private val PLAYER_ID = UUID.fromString("67e55044-10b1-426f-9247-bb680e5fe0c8")
    }
}
