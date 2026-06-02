package gg.grounds.proxy.velocity.handler

import io.nats.client.Connection
import io.nats.client.ConnectionListener
import io.nats.client.Dispatcher
import io.nats.client.Nats
import io.nats.client.Options
import io.nats.client.Subscription
import java.nio.charset.StandardCharsets
import java.time.Duration
import org.slf4j.Logger

class NatsHandler(private val natsUrl: String, private val logger: Logger) : AutoCloseable {
    private lateinit var connection: Connection
    private lateinit var dispatcher: Dispatcher

    fun connect() {
        val options =
            Options.Builder()
                .server(natsUrl)
                .connectionName("plugin-proxy")
                .reconnectWait(Duration.ofSeconds(2))
                .maxReconnects(-1)
                .connectionListener { conn, event ->
                    when (event) {
                        ConnectionListener.Events.RECONNECTED ->
                            logger.info("NATS reconnected (server={})", conn.connectedUrl)
                        ConnectionListener.Events.DISCONNECTED -> logger.warn("NATS disconnected")
                        ConnectionListener.Events.CLOSED -> logger.info("NATS connection closed")
                        else -> {}
                    }
                }
                .build()
        connection = Nats.connect(options)
        dispatcher = connection.createDispatcher()
        logger.info("Connected to NATS (url={})", natsUrl)
    }

    val isConnected: Boolean
        get() = this::connection.isInitialized && connection.status == Connection.Status.CONNECTED

    fun publish(subject: String, data: String) {
        if (isConnected) {
            connection.publish(subject, data.toByteArray(StandardCharsets.UTF_8))
        }
    }

    fun subscribe(subject: String, handler: (String) -> Unit): Subscription {
        return dispatcher.subscribe(subject) { msg ->
            try {
                val payload = String(msg.data, StandardCharsets.UTF_8)
                handler(payload)
            } catch (e: Exception) {
                logger.warn("Error handling message on {}: {}", subject, e.message)
            }
        }
    }

    fun unsubscribe(subscription: Subscription) {
        dispatcher.unsubscribe(subscription)
    }

    override fun close() {
        if (this::dispatcher.isInitialized) {
            dispatcher.drain(Duration.ofSeconds(2))
        }
        if (this::connection.isInitialized) {
            connection.close()
            logger.info("Disconnected from NATS")
        }
    }
}
