package gg.grounds.proxy.velocity.maintenance

import java.time.Instant
import java.util.UUID

/**
 * Keeps Velocity permission lookup at the listener boundary and makes whitelist precedence
 * explicit.
 */
object MaintenanceAccess {
    const val BYPASS_PERMISSION = "grounds.maintenance.bypass"

    fun admission(
        engine: MaintenanceEngine,
        uuid: UUID,
        hasPermission: () -> Boolean,
        now: Instant,
    ): MaintenanceEngine.Admission =
        engine.admission(uuid, bypass(engine, uuid, hasPermission), now)

    fun shouldKick(
        engine: MaintenanceEngine,
        uuid: UUID,
        hasPermission: () -> Boolean,
        now: Instant,
    ): Boolean = engine.shouldKick(uuid, bypass(engine, uuid, hasPermission), now)

    private fun bypass(
        engine: MaintenanceEngine,
        uuid: UUID,
        hasPermission: () -> Boolean,
    ): Boolean = engine.whitelisted(uuid) || runCatching(hasPermission).getOrDefault(false)
}
