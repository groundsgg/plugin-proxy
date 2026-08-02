package gg.grounds.proxy.api

import java.util.UUID

/**
 * How a player's rank should be drawn, across the whole network.
 *
 * `service-permissions` has shipped this since the beginning — `PlayerPermissionSnapshot` carries
 * `RoleMetadata { key, name, prefix, color, sort_order }`, and plugin-permissions fetches and
 * caches every field of it. Nothing has ever read them, so a rank has been invisible everywhere a
 * player looks: chat, the tab list, `/online`.
 *
 * This is the seam that makes them readable without every plugin growing a permissions client.
 * Registered into the [ProxyServiceRegistry] by plugin-permissions, which already holds the
 * snapshot; consumed by whoever draws a name.
 *
 * With nothing registered [highestRoleOf] returns null and callers draw the name plainly, which is
 * exactly today's behaviour — so a consumer of this still works on a proxy without
 * plugin-permissions.
 */
interface PlayerRoleQuery {
    /**
     * The role that should decide how this player is drawn, or null if they have none.
     *
     * "Highest" is the lowest [PlayerRole.sortOrder], matching how the tab list orders people: a
     * player is usually in several roles at once and only one of them can colour their name.
     *
     * Reads an in-memory snapshot, so it is safe on the per-message render path.
     */
    fun highestRoleOf(playerId: UUID): PlayerRole?
}

/**
 * A rank as it is displayed.
 *
 * Mirrors `RoleMetadata` from the permissions snapshot, minus the parts only a permission check
 * cares about. [colour] is a `#rrggbb` string because that is what the service stores; it is
 * deliberately not parsed here, so this module stays free of an Adventure dependency and a
 * malformed value is the caller's problem to ignore rather than a failure at the registry boundary.
 *
 * @param key stable identifier, e.g. `admin`
 * @param name what a human reads, e.g. `Admin`
 * @param prefix optional tag shown before the name, e.g. `[Admin] `
 * @param colour `#rrggbb`, or null to draw the name in the default text colour
 * @param sortOrder lower sorts first; ties break on [key]
 */
data class PlayerRole(
    val key: String,
    val name: String,
    val prefix: String? = null,
    val colour: String? = null,
    val sortOrder: Int = 0,
)
