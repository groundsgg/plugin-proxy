package gg.grounds.proxy.velocity.tab

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServerDisplayIdsTest {
    @Test
    fun `the replica id is the last hyphen segment`() {
        assertEquals("s9fwt", ServerDisplayIds.idOf("lobby-nl-ams1-tr9pf-s9fwt"))
    }

    @Test
    fun `a name with no hyphen is used whole`() {
        assertEquals("lobby", ServerDisplayIds.idOf("lobby"))
    }

    @Test
    fun `a trailing hyphen does not produce an empty id`() {
        assertEquals("lobby-", ServerDisplayIds.idOf("lobby-"))
    }
}
