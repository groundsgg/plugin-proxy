package gg.grounds.proxy.velocity.welcome

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.player.CookieReceiveEvent
import com.velocitypowered.api.event.player.PlayerChannelRegisterEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.network.HandshakeIntent
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.scheduler.ScheduledTask
import gg.grounds.i18n.Translations
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.key.Key

/**
 * Observe installation, not UDP presence: a muted or disconnected mod still counts as installed.
 */
internal class WelcomeListener(
    private val messages: Translations,
    private val renderer: WelcomeMessage,
    private val schedule: (Runnable) -> ScheduledTask,
    private val logoReady: (Player) -> Boolean,
    private val iconsReady: (Player) -> Boolean = { false },
    private val legacySchedule: (Runnable) -> ScheduledTask = schedule,
) : AutoCloseable {
    // Connection objects prevent a delayed task or old disconnect from affecting a reconnect.
    private val sessions = ConcurrentHashMap<Player, Join>()
    @Volatile private var closed = false

    private class Join(transfer: Boolean) {
        var voiceInstalled = false
        var welcomed: Boolean? = if (transfer) null else false
        var cookieRequested = false
        var readyRequested = false
        var clientReady = false
        var connected = false
        var scheduled = false
        var sent = false
        var task: ScheduledTask? = null
    }

    private fun session(player: Player): Join =
        sessions.computeIfAbsent(player) {
            Join(player.handshakeIntent == HandshakeIntent.TRANSFER)
        }

    @Subscribe(order = PostOrder.FIRST)
    fun onLogin(event: PostLoginEvent) {
        val player = event.player
        if (closed || player.protocolVersion < ProtocolVersion.MINECRAFT_1_20_5) return
        val join = session(player)
        synchronized(join) {
            if (closed || sessions[player] !== join) return
            if (player.handshakeIntent == HandshakeIntent.TRANSFER) {
                join.cookieRequested = true
                player.requestCookie(COOKIE_KEY)
            } else {
                // Reset before PreferredRegionListener can transfer this fresh network join.
                player.storeCookie(COOKIE_KEY, byteArrayOf(0))
            }
        }
    }

    @Subscribe
    fun onCookie(event: CookieReceiveEvent) {
        if (event.originalKey != COOKIE_KEY && event.originalKey != READY_KEY) return
        val player = event.player
        val join = sessions[player] ?: return
        synchronized(join) {
            if (closed || sessions[player] !== join) return
            if (event.originalKey == READY_KEY) {
                if (!join.readyRequested) return
                join.readyRequested = false
                join.clientReady = true
            } else {
                if (!join.cookieRequested) return
                join.cookieRequested = false
                join.welcomed = event.originalData?.contentEquals(byteArrayOf(1)) == true
            }
            event.result = CookieReceiveEvent.ForwardResult.handled()
            scheduleIfNeeded(player, join)
        }
    }

    @Subscribe(priority = Short.MAX_VALUE, async = false)
    fun onChannels(event: PlayerChannelRegisterEvent): EventTask {
        if (event.channels.any { it.id.startsWith("voicechat:") }) markInstalled(event.player)
        return continueAsync()
    }

    // Observe on the packet thread before async handlers can let a later cookie reply overtake us.
    @Subscribe(priority = Short.MAX_VALUE, async = false)
    fun onPluginMessage(event: PluginMessageEvent): EventTask {
        if (event.identifier.id == VOICE_CHANNEL && event.data.size == 4) {
            (event.source as? Player)?.let(::markInstalled)
        }
        // Leave the result unchanged: the backend must receive the original handshake.
        return continueAsync()
    }

    // Velocity continues later void handlers on our current thread unless we explicitly hand off.
    // Capture only the installation bit synchronously; preserve async execution for other plugins.
    private fun continueAsync(): EventTask = EventTask.async {}

    private fun markInstalled(player: Player) {
        if (closed) return
        val join = session(player)
        synchronized(join) { join.voiceInstalled = true }
    }

    @Subscribe
    fun onServerConnected(event: ServerPostConnectEvent) {
        val player = event.player
        if (closed || event.previousServer != null) return
        val join = session(player)
        synchronized(join) {
            join.connected = true
            scheduleIfNeeded(player, join)
        }
    }

    /** Called under the connection's monitor, after either backend connect or cookie reply. */
    private fun scheduleIfNeeded(player: Player, join: Join) {
        if (
            closed ||
                sessions[player] !== join ||
                !join.connected ||
                join.welcomed != false ||
                join.scheduled
        )
            return
        if (player.protocolVersion >= ProtocolVersion.MINECRAFT_1_20_5 && !join.clientReady) {
            if (!join.readyRequested) {
                join.readyRequested = true
                // Queued after JoinGame: the client processes join-time mod announcements before
                // replying on its main thread. Unlike a timer this costs only a network round trip.
                player.requestCookie(READY_KEY)
            }
            return
        }
        join.scheduled = true
        val scheduler =
            if (player.protocolVersion >= ProtocolVersion.MINECRAFT_1_20_5) schedule
            else legacySchedule
        join.task = scheduler(Runnable { deliver(player, join) })
    }

    private fun deliver(player: Player, join: Join) {
        synchronized(join) {
            if (closed || sessions[player] !== join || join.sent || !player.isActive) return
            join.sent = true
            join.task = null
            player.sendMessage(
                renderer.render(
                    player.username,
                    messages.localeOf(player),
                    logoReady(player),
                    join.voiceInstalled,
                    iconsReady(player),
                )
            )
            join.welcomed = true
            if (player.protocolVersion >= ProtocolVersion.MINECRAFT_1_20_5) {
                player.storeCookie(COOKIE_KEY, byteArrayOf(1))
            }
        }
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        val join = sessions.remove(event.player) ?: return
        synchronized(join) { join.task?.cancel() }
    }

    override fun close() {
        closed = true
        sessions.values.forEach { join -> synchronized(join) { join.task?.cancel() } }
        sessions.clear()
    }

    companion object {
        private val COOKIE_KEY = Key.key("grounds:welcome")
        private val READY_KEY = Key.key("grounds:welcome_ready")
        const val VOICE_CHANNEL = "voicechat:request_secret"
        const val LEGACY_GRACE_SECONDS = 5L
    }
}
