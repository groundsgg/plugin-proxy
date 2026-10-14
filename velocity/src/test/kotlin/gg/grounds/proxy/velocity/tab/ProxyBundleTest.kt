package gg.grounds.proxy.velocity.tab

import gg.grounds.i18n.BundleCheck
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProxyBundleTest {

    @Test
    fun `every supported language has every message with the English tags`() {
        val problems =
            BundleCheck.problems(
                "gg.grounds.proxy.messages",
                javaClass.classLoader,
                ProxyMessage.entries,
            )
        assertEquals(emptyList<String>(), problems, problems.joinToString("\n"))
    }
}
