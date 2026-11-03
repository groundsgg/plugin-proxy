package gg.grounds.proxy.velocity.announcements

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnouncementConfigStoreTest {
    @Test
    fun `reads authoritative snapshots with rotated token and conditional revision`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var expected = "first"
        var requests = 0
        server.createContext(
            "/v1/config/apps/velocity/envs/stage/namespaces/announcements/snapshot"
        ) { exchange ->
            assertEquals("Bearer $expected", exchange.requestHeaders.getFirst("Authorization"))
            requests++
            if (requests == 1) {
                val body =
                    """{"version":7,"documents":[{"namespace":"announcements","configKey":"active","contentJson":"{\"schemaVersion\":1,\"announcements\":[]}","version":7}]}"""
                        .toByteArray()
                exchange.responseHeaders.add("ETag", "\"7\"")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } else {
                assertEquals("\"7\"", exchange.requestHeaders.getFirst("If-None-Match"))
                exchange.sendResponseHeaders(304, -1)
                exchange.close()
            }
        }
        server.start()
        try {
            AnnouncementConfigStore(
                    URI("http://127.0.0.1:${server.address.port}"),
                    "stage",
                    { expected },
                )
                .use { store ->
                    val first = store.read(null) as AnnouncementSnapshot.Changed
                    assertEquals(7, first.version)
                    assertTrue(first.settings!!.announcements.isEmpty())
                    expected = "second"
                    assertEquals(AnnouncementSnapshot.Unchanged, store.read(7))
                }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `malformed matching content retains the previous revision and display`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var calls = 0
        val revisions = mutableListOf<String?>()
        server.createContext("/") { exchange ->
            revisions += exchange.requestHeaders.getFirst("If-None-Match")
            calls++
            val doc =
                com.google.gson.JsonObject().apply {
                    addProperty("namespace", "announcements")
                    addProperty("configKey", "active")
                    if (calls != 2)
                        addProperty("contentJson", """{"schemaVersion":1,"announcements":[]}""")
                }
            val root =
                com.google.gson.JsonObject().apply {
                    addProperty("version", calls)
                    add("documents", com.google.gson.JsonArray().apply { add(doc) })
                }
            val body = root.toString().toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            AnnouncementConfigStore(
                    URI("http://127.0.0.1:${server.address.port}"),
                    "stage",
                    { null },
                )
                .use { store ->
                    val applied = mutableListOf<AnnouncementSettings?>()
                    val sync = AnnouncementSynchronizer(store::read, { applied += it }, {})
                    sync.refresh()
                    sync.refresh()
                    sync.refresh()
                    assertEquals(2, applied.size)
                    assertEquals(
                        listOf(
                            null,
                            34.toChar().toString() + "1" + 34.toChar(),
                            34.toChar().toString() + "1" + 34.toChar(),
                        ),
                        revisions,
                    )
                }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `foreign app documents cannot block clearing announcements`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/config/apps/velocity/envs/stage/snapshot") { exchange ->
            val body =
                """{"version":8,"documents":[{"namespace":"motd","configKey":"active","contentJson":"${"x".repeat(270000)}"}]}"""
                    .toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext(
            "/v1/config/apps/velocity/envs/stage/namespaces/announcements/snapshot"
        ) { exchange ->
            val body = """{"version":8,"documents":[]}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            AnnouncementConfigStore(
                    URI("http://127.0.0.1:${server.address.port}"),
                    "stage",
                    { null },
                )
                .use { store ->
                    val next = store.read(7) as AnnouncementSnapshot.Changed
                    assertNull(next.settings)
                    assertEquals(8L, next.version)
                }
        } finally {
            server.stop(0)
        }
    }
}
