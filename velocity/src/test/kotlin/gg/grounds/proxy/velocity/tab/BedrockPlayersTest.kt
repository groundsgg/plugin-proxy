package gg.grounds.proxy.velocity.tab

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class BedrockPlayersTest {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val ordinaryUuid = UUID.fromString("12345678-1234-4234-8234-123456789abc")

    object Api {
        @JvmStatic fun getInstance(): Api = this

        fun isFloodgatePlayer(id: UUID): Boolean = id.toString().startsWith("12345678")
    }

    @Test
    fun `Floodgate recognizes Bedrock profiles with an ordinary-shaped UUID`() {
        assertTrue(BedrockPlayers(logger) { Api::class.java }.isBedrock(ordinaryUuid))
    }

    @Test
    fun `absent API can be discovered after plugin initialization`() {
        var api: Class<*>? = null
        val players = BedrockPlayers(logger) { api }
        assertFalse(players.isBedrock(ordinaryUuid))
        api = Api::class.java
        assertTrue(players.isBedrock(ordinaryUuid))
    }

    @Test
    fun `missing or incompatible API degrades to XUID-derived Bedrock UUID detection`() {
        val id = UUID.fromString("00000000-0000-0000-0009-01f64d9a38a8")
        assertTrue(BedrockPlayers(logger) { null }.isBedrock(id))
        assertFalse(BedrockPlayers(logger) { String::class.java }.isBedrock(ordinaryUuid))
    }
}
