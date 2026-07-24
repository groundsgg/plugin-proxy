package gg.grounds.proxy.velocity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayerCountPayloadTest {

    @Test
    fun `reads the total`() {
        assertEquals(0, parsePlayerCount("""{"total":0}"""))
        assertEquals(1234, parsePlayerCount("""{"total":1234}"""))
    }

    @Test
    fun `tolerates whitespace and extra fields`() {
        assertEquals(7, parsePlayerCount("""{"region":"eu", "total" : 7 , "x":1}"""))
    }

    /** Anything unrecognised means "keep the previous value", never "zero players". */
    @Test
    fun `returns null rather than guessing`() {
        assertNull(parsePlayerCount(""))
        assertNull(parsePlayerCount("{}"))
        assertNull(parsePlayerCount("""{"total":}"""))
        assertNull(parsePlayerCount("""{"total":"many"}"""))
        assertNull(parsePlayerCount("""{"totals":5}"""))
    }
}
