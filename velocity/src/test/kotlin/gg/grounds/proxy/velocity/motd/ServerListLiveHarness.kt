package gg.grounds.proxy.velocity.motd

import com.google.gson.JsonParser
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.proxy.velocity.maintenance.MaintenanceConfigStore
import gg.grounds.proxy.velocity.maintenance.MaintenanceEngine
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import org.slf4j.Logger

/** Test-only entry point. The validation JAR replaces the production plugin descriptor. */
class ServerListLiveHarness
@Inject
constructor(private val proxy: ProxyServer, private val logger: Logger) {
    private val shared = Path.of(System.getProperty("motd.harness.document"))
    private val region = System.getProperty("motd.harness.region")
    private val local = System.getProperty("motd.harness.local").toInt()
    @Volatile private var network = 0
    private val maintenance = MaintenanceEngine()
    private val store =
        object : MotdStore {
            override fun read(): MotdDocument? {
                val root = JsonParser.parseString(Files.readString(shared)).asJsonObject
                network = root.get("networkPlayers").asInt
                val m = root.get("maintenance")
                maintenance.adopt(
                    if (m == null || m.isJsonNull) null
                    else MaintenanceConfigStore.parse(m.toString())
                )
                return MotdDocument.fromJson(root.get("motd").toString())
            }

            override fun write(document: MotdDocument, updatedBy: String) =
                error("read-only harness")

            override fun clear(deletedBy: String): Boolean = error("read-only harness")
        }
    private val manager = MotdManager(store, region, region, logger)
    private val listener =
        ServerListPingListener({ manager }, { network }, { maintenance }, { region }, { region })

    @Subscribe
    fun initialize(event: ProxyInitializeEvent) {
        manager.refresh()
        proxy.scheduler
            .buildTask(this, Runnable { manager.refresh() })
            .repeat(Duration.ofMillis(200))
            .schedule()
    }

    @Subscribe
    fun ping(event: ProxyPingEvent) {
        event.ping = event.ping.asBuilder().onlinePlayers(local).build()
        listener.onProxyPing(event)
    }
}
