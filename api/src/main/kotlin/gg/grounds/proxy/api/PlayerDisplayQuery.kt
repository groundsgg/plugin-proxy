package gg.grounds.proxy.api

import java.util.UUID

/** Authenticated edition and the corresponding user-facing name for one technical profile. */
interface PlayerDisplayQuery {
    fun displayOf(playerId: UUID): PlayerDisplaySnapshot?
}

data class PlayerDisplaySnapshot(
    val technicalName: String,
    val displayName: String,
    val isBedrock: Boolean?,
)

object PlayerDisplayNames {
    /** The technical dot is removed only for a profile independently verified as Bedrock. */
    @JvmStatic
    fun displayName(technicalName: String, isBedrock: Boolean?): String =
        if (isBedrock == true && technicalName.startsWith('.')) technicalName.drop(1)
        else technicalName
}
