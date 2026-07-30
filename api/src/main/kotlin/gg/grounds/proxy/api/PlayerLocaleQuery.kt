package gg.grounds.proxy.api

import java.util.Locale
import java.util.UUID

/**
 * A player's chosen interface language, across the whole network.
 *
 * Registered into the [ProxyServiceRegistry] by whichever plugin owns the preference — today
 * plugin-player, which caches it per online player and persists it through service-player. A
 * localized plugin consults this before falling back to the locale the client announced:
 * ```kotlin
 * LocaleResolver { audience ->
 *     ProxyServiceRegistry.get(PlayerLocaleQuery::class.java)
 *         ?.let { q -> audience.get(Identity.UUID).map(q::localeOf).orElse(null) }
 *         ?: LocaleResolver.FROM_AUDIENCE.localeOf(audience)
 * }
 * ```
 *
 * With nothing registered the lookup is simply skipped and the client locale wins, so a plugin that
 * depends on this still works on a proxy where plugin-player is absent.
 */
interface PlayerLocaleQuery {
    /**
     * The player's stored language, or null when they have chosen none — in which case the caller
     * uses the locale the client announced. Reads an in-memory cache, so it is safe on the
     * per-message render path.
     */
    fun localeOf(playerId: UUID): Locale?
}
