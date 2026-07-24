package gg.grounds.proxy.velocity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegionCatalogTest {

    @Test
    fun `parses codes hosts and ports`() {
        val catalog =
            RegionCatalog.fromEnvironment(
                "nl-ams1=nl-ams1.stage.grnds.io,us-nyc1=nyc1.stage.grnds.io:30565"
            )

        assertEquals(listOf("nl-ams1", "us-nyc1"), catalog.codes)
        assertEquals(25565, catalog["nl-ams1"]!!.port)
        assertEquals(30565, catalog["us-nyc1"]!!.port)
        assertEquals("nyc1.stage.grnds.io", catalog["us-nyc1"]!!.host)
        assertTrue(catalog.problems.isEmpty())
    }

    @Test
    fun `lookup ignores case`() {
        val catalog = RegionCatalog.fromEnvironment("nl-ams1=host")

        assertEquals("nl-ams1", catalog["NL-AMS1"]?.code)
    }

    /** A typo in one region must not cost the others — the proxy still has to start. */
    @Test
    fun `drops bad entries and keeps the rest`() {
        val catalog =
            RegionCatalog.fromEnvironment("good=host,broken,bad=host:99999,=host,dup=a,dup=b")

        assertEquals(listOf("good", "dup"), catalog.codes)
        assertEquals("a", catalog["dup"]?.host)
        assertEquals(4, catalog.problems.size)
    }

    @Test
    fun `unset is empty rather than an error`() {
        val catalog = RegionCatalog.fromEnvironment(null)

        assertTrue(catalog.regions.isEmpty())
        assertTrue(catalog.problems.isEmpty())
        assertNull(catalog["nl-ams1"])
    }
}
