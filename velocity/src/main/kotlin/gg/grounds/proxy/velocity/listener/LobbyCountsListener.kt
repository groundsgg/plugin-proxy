package gg.grounds.proxy.velocity.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.ChannelIdentifier
import gg.grounds.proxy.api.ProxyService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import org.slf4j.Logger

/**
 * Answers a backend asking how many players each server holds across the whole network.
 *
 * A backend cannot work this out for itself, and neither can one proxy: Velocity's own
 * `playersConnected` counts the players on *this* proxy, so with two proxies in front of one lobby
 * each sees half of it. [ProxyService.getNetworkPlayerCounts] is the only answer that is about the
 * network, which is why this listener exists rather than the backend counting something locally.
 *
 * An unknown count is sent as an empty body rather than as zeros. A backend that asked and got
 * nothing back knows the network could not be reached; zeros would be indistinguishable from an
 * empty network, and showing a number that is silently wrong is the failure this whole path is
 * built to avoid.
 */
class LobbyCountsListener(
    private val identifier: ChannelIdentifier,
    private val proxyService: () -> ProxyService?,
    private val logger: Logger,
) {
    @Subscribe
    fun onPluginMessage(event: PluginMessageEvent) {
        if (event.identifier != identifier) return
        val source = event.source as? ServerConnection ?: return

        // Handled either way: this channel is ours, and an unparseable message on it must not be
        // forwarded to the client.
        event.result = PluginMessageEvent.ForwardResult.handled()

        if (!LobbyCountsPayload.isCountsRequest(event.data)) return

        try {
            source.sendPluginMessage(
                identifier,
                LobbyCountsPayload.response(proxyService()?.getNetworkPlayerCounts()?.byServer),
            )
        } catch (error: Exception) {
            logger.warn("Could not answer a lobby count request", error)
        }
    }

    companion object {
        /** Same shape as the BungeeCord channel a backend already speaks: UTF subchannel first. */
        const val CHANNEL_ID = LobbyCountsPayload.CHANNEL_ID
    }
}

/**
 * The wire format, kept apart from the listener so it can be tested without a proxy.
 *
 * Length-prefixed UTF strings, subchannel first — the same shape the BungeeCord channel uses, so a
 * backend needs no second codec to speak this one.
 */
object LobbyCountsPayload {
    const val CHANNEL_ID = "grounds:lobby"
    const val COUNTS = "Counts"

    /** True when [message] is a request this listener should answer. */
    fun isCountsRequest(message: ByteArray): Boolean = decode(message)?.firstOrNull() == COUNTS

    /**
     * `server=count` pairs, comma separated.
     *
     * A null [byServer] renders an empty body rather than zeros: a backend that asked and got
     * nothing back knows the network could not be reached, while zeros are indistinguishable from
     * an empty network — and a number that is silently wrong is what this whole path avoids.
     */
    fun response(byServer: Map<String, Int>?): ByteArray =
        encode(
            COUNTS,
            byServer?.entries?.joinToString(",") { (server, count) -> "$server=$count" } ?: "",
        )

    fun request(): ByteArray = encode(COUNTS)

    fun decode(message: ByteArray): List<String>? =
        try {
            val parts = mutableListOf<String>()
            DataInputStream(ByteArrayInputStream(message)).use { input ->
                while (input.available() > 0) parts += input.readUTF()
            }
            parts.takeIf { it.isNotEmpty() }
        } catch (_: IOException) {
            null
        }

    private fun encode(vararg parts: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out -> parts.forEach(out::writeUTF) }
        return bytes.toByteArray()
    }
}
