package gg.grounds.proxy.velocity.tab

import gg.grounds.proxy.api.PlayerRole
import java.util.Locale

object TabRankLabels {
    fun resolve(role: PlayerRole): String? =
        when (role.key.trim().lowercase(Locale.ROOT)) {
            "admin",
            "administrator" -> "ADMIN"
            "dev",
            "developer" -> "DEV"
            "mod",
            "moderator" -> "MOD"
            "user",
            "player",
            "default" -> "USER"
            "support",
            "supporter" -> "SUP"
            "builder" -> "BUILD"
            else -> role.name.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
        }
}
