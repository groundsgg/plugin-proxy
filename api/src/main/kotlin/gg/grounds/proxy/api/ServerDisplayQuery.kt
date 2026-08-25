package gg.grounds.proxy.api

/**
 * How a backend should be named in player-facing UI.
 *
 * plugin-agones knows the Agones GameServer name and the `grounds/server-type` label.
 * plugin-proxy draws the footer and must not import Agones types, so it asks the registry.
 *
 * With nothing registered, callers still show [ServerDisplay.id] parsed from the Velocity
 * server name and omit the kind, rather than printing the full pod name.
 */
interface ServerDisplayQuery {
    fun displayOf(serverName: String): ServerDisplay?
}

/**
 * @param kind `grounds/server-type` value, e.g. `lobby`
 * @param id last `-` segment of the GameServer name, e.g. `s9fwt`
 */
data class ServerDisplay(val kind: String, val id: String)
