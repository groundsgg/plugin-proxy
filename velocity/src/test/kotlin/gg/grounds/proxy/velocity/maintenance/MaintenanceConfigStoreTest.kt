package gg.grounds.proxy.velocity.maintenance

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MaintenanceConfigStoreTest {
    @Test
    fun `accepts canonical UUIDs of every version including floodgate UUIDs`() {
        val document = MaintenanceConfigStore.parse(json("123e4567-e89b-12d3-a456-426614174000"))
        assertEquals("123e4567-e89b-12d3-a456-426614174000", document.id.toString())
    }

    @Test
    fun `normalizes UUID case and rejects fractional offsets`() {
        assertEquals(
            "123e4567-e89b-12d3-a456-426614174000",
            MaintenanceConfigStore.parse(json("123E4567-E89B-12D3-A456-426614174000")).id.toString(),
        )
        assertThrows<MaintenanceStoreException> {
            MaintenanceConfigStore.parse(
                json("123e4567-e89b-12d3-a456-426614174000").replace("[60]", "[60.5]")
            )
        }
    }

    @Test
    fun `rejects wrong scalar types and duplicate whitelist entries`() {
        val valid = json("123e4567-e89b-12d3-a456-426614174000")
        assertThrows<MaintenanceStoreException> {
            MaintenanceConfigStore.parse(
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\"")
            )
        }
        assertThrows<MaintenanceStoreException> {
            MaintenanceConfigStore.parse(
                valid.replace(
                    "\"whitelist\":[]",
                    "\"whitelist\":[\"123e4567-e89b-12d3-a456-426614174000\",\"123e4567-e89b-12d3-a456-426614174000\"]",
                )
            )
        }
    }

    @Test
    fun `a stalled response body times out and the next poll can recover`() {
        val bodyStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            if (requests.getAndIncrement() > 0) {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
                return@createContext
            }
            exchange.sendResponseHeaders(200, 1000)
            exchange.responseBody.write("{".toByteArray())
            exchange.responseBody.flush()
            bodyStarted.countDown()
            release.await(5, TimeUnit.SECONDS)
            exchange.close()
        }
        server.start()
        val http = HttpClient.newHttpClient()
        val store =
            MaintenanceConfigStore(
                "velocity",
                "stage",
                URI("http://127.0.0.1:${server.address.port}"),
                http,
                Duration.ofMillis(750),
            )
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(3)) {
                assertThrows<MaintenanceStoreException> { store.read() }
            }
            assertTrue(bodyStarted.await(1, TimeUnit.SECONDS))
            release.countDown()
            assertNull(store.read())
        } finally {
            release.countDown()
            server.stop(0)
            store.close()
        }
    }

    @Test
    fun `rejects shortened UUID forms instead of silently padding them`() {
        assertThrows<MaintenanceStoreException> { MaintenanceConfigStore.parse(json("1-1-1-1-1")) }
        assertThrows<MaintenanceStoreException> {
            MaintenanceConfigStore.parse(
                json("123e4567-e89b-12d3-a456-426614174000")
                    .replace("\"whitelist\":[]", "\"whitelist\":[\"1-1-1-1-1\"]")
            )
        }
    }

    private fun json(id: String) =
        """{"schemaVersion":1,"enabled":false,"id":"$id","startsAt":"2026-10-19T20:00:00.000Z","endsAt":null,"estimatedEndAt":null,"reason":"work","joinMessage":"join","motd":"motd","kickExisting":true,"whitelist":[],"announcementSeconds":[60],"announcementMessage":"soon"}"""
}
