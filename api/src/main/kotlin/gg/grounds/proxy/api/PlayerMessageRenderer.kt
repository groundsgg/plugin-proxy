package gg.grounds.proxy.api

import java.util.UUID
import net.kyori.adventure.text.Component

/** Optional per-player renderer for semantic Components delivered by [ProxyService]. */
fun interface PlayerMessageRenderer {
    /** Returns a rendered component, or null when this provider does not own [message]. */
    fun render(playerId: UUID, message: Component): Component?
}

/** Resolves the current renderer at delivery time and safely preserves unhandled messages. */
object PlayerMessageRenderers {
    @JvmStatic
    fun renderOrOriginal(playerId: UUID, message: Component): Component =
        try {
            ProxyServiceRegistry.get(PlayerMessageRenderer::class.java)?.render(playerId, message)
                ?: message
        } catch (_: RuntimeException) {
            message
        } catch (_: LinkageError) {
            message
        }
}
