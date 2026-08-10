package gg.grounds.proxy.velocity.motd

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The store against a stand-in service-config.
 *
 * The two things worth pinning: reads and writes go to *different* halves of the API, because
 * service-config grants them separately — a proxy that may show the MOTD need not be allowed to
 * change it. And a refusal has to arrive in chat as the sentence service-config wrote, since that
 * is the only place a missing writer grant is explained.
 */
class MotdConfigStoreTest {

    @Test
    fun `no MOTD set is null, not an error`() {
        // The normal state of a fresh network. The caller leaves Velocity's own MOTD alone.
        withServer({ _ -> 404 to """{"code":"not_found","detail":"No such document."}""" }) { store
            ->
            assertNull(store.read())
        }
    }

    @Test
    fun `a stored MOTD is unwrapped from the document`() {
        withServer({ _ ->
            200 to
                """{"namespace":"motd","configKey":"active","contentJson":"{\"text\":\"hello\"}","version":3}"""
        }) { store ->
            assertEquals("hello", store.read()?.text)
        }
    }

    @Test
    fun `reads use the consumer API and writes the admin API`() {
        val seen = CopyOnWriteArrayList<String>()
        withServer({ exchange ->
            seen.add("${exchange.requestMethod} ${exchange.requestURI.path}")
            when (exchange.requestMethod) {
                "GET" ->
                    200 to
                        """{"contentJson":"{\"text\":\"hello\"}","namespace":"motd","configKey":"active","version":1}"""
                "DELETE" -> 200 to """{"deleted":true,"version":2}"""
                else -> 200 to """{"version":2}"""
            }
        }) { store ->
            store.read()
            store.write(MotdDocument(text = "hi"), updatedBy = "hendrik")
            store.clear(deletedBy = "hendrik")
        }

        assertEquals(
            listOf(
                "GET /v1/config/apps/velocity/envs/stage/namespaces/motd/documents/active",
                "PUT /v1/config/admin/apps/velocity/envs/stage/namespaces/motd/documents/active",
                "DELETE /v1/config/admin/apps/velocity/envs/stage/namespaces/motd/documents/active",
            ),
            seen,
        )
    }

    @Test
    fun `a refusal surfaces the sentence service-config wrote`() {
        withServer({ _ ->
            403 to
                """{"title":"Forbidden","status":403,"code":"forbidden","detail":"replace document on app 'velocity' requires admin or a configured writer"}"""
        }) { store ->
            val error =
                assertThrows<MotdStoreException> {
                    store.write(MotdDocument(text = "hi"), updatedBy = "hendrik")
                }
            assertTrue(error.message!!.contains("configured writer"))
        }
    }

    @Test
    fun `a failure with no readable body still says what was attempted`() {
        withServer({ _ -> 500 to "" }) { store ->
            val error = assertThrows<MotdStoreException> { store.read() }
            assertTrue(error.message!!.contains("read the MOTD"))
        }
    }

    @Test
    fun `clearing reports whether there was anything to clear`() {
        withServer({ _ -> 200 to """{"deleted":false,"version":4}""" }) { store ->
            assertFalse(store.clear(deletedBy = "hendrik"))
        }
    }

    private fun withServer(
        handler: (HttpExchange) -> Pair<Int, String>,
        block: (MotdConfigStore) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.requestBody.readBytes()
            val (status, body) = handler(exchange)
            val bytes = body.toByteArray()
            if (bytes.isEmpty()) {
                exchange.sendResponseHeaders(status, -1)
            } else {
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        val store = MotdConfigStore.open("velocity", "stage", "127.0.0.1:${server.address.port}")
        try {
            block(store)
        } finally {
            store.close()
            server.stop(0)
        }
    }
}
