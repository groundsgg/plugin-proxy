package gg.grounds.proxy.velocity.tab

import gg.grounds.proxy.api.PlayerRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TabRankLabelsTest {
    @Test
    fun `stable role keys use compact tab labels`() {
        assertEquals("ADMIN", TabRankLabels.resolve(PlayerRole("administrator", "Owner")))
        assertEquals("DEV", TabRankLabels.resolve(PlayerRole("developer", "Platform Engineer")))
        assertEquals("MOD", TabRankLabels.resolve(PlayerRole("moderator", "Community Guardian")))
        assertEquals("USER", TabRankLabels.resolve(PlayerRole("player", "Member")))
        assertEquals("SUP", TabRankLabels.resolve(PlayerRole("supporter", "Helper")))
        assertEquals("BUILD", TabRankLabels.resolve(PlayerRole("builder", "Architect")))
    }

    @Test
    fun `unknown roles retain an uppercase readable name`() {
        assertEquals(
            "COMMUNITY LEAD",
            TabRankLabels.resolve(PlayerRole("community-lead", "Community Lead")),
        )
    }
}
