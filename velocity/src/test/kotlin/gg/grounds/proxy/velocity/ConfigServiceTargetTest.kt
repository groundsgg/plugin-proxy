package gg.grounds.proxy.velocity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConfigServiceTargetTest {

    @Test
    fun `uses the declared config service contract`() {
        val environment =
            mapOf(
                "CONFIG_SERVICE_URL" to "service-config.default.svc.cluster.local:9000",
                "CONFIG_GRPC_TARGET" to "legacy-config:9000",
            )

        assertEquals(
            "service-config.default.svc.cluster.local:9000",
            resolveConfigServiceTarget(environment::get),
        )
    }

    @Test
    fun `falls back to the legacy config target`() {
        val environment = mapOf("CONFIG_GRPC_TARGET" to "legacy-config:9000")

        assertEquals("legacy-config:9000", resolveConfigServiceTarget(environment::get))
    }

    @Test
    fun `ignores blank config targets`() {
        val environment = mapOf("CONFIG_SERVICE_URL" to "  ", "CONFIG_GRPC_TARGET" to "")

        assertNull(resolveConfigServiceTarget(environment::get))
    }
}
