package gg.grounds.proxy.velocity.tab

import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.proxy.api.PlayerDisplayNames
import gg.grounds.proxy.api.PlayerDisplayQuery
import gg.grounds.proxy.api.PlayerDisplaySnapshot
import gg.grounds.proxy.api.PlayerSessionQuery
import gg.grounds.proxy.api.ProxyServiceRegistry
import java.util.UUID

internal class PlayerDisplayQueryImpl(
    private val proxy: ProxyServer,
    private val roster: BedrockRoster,
) : PlayerDisplayQuery {
    override fun displayOf(playerId: UUID): PlayerDisplaySnapshot? {
        val player = proxy.getPlayer(playerId).orElse(null)
        val name =
            player?.username
                ?: ProxyServiceRegistry.get(PlayerSessionQuery::class.java)
                    ?.getSession(playerId)
                    ?.name
                ?: return null
        val bedrock = roster.classification(playerId)
        return PlayerDisplaySnapshot(
            technicalName = name,
            displayName = PlayerDisplayNames.displayName(name, bedrock),
            isBedrock = bedrock,
        )
    }
}
