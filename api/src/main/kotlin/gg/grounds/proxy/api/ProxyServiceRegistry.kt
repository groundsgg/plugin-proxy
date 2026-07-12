package gg.grounds.proxy.api

import java.util.concurrent.ConcurrentHashMap

/**
 * How Velocity plugins hand each other capabilities at runtime.
 *
 * Two providers meet here:
 * - **plugin-proxy** registers [ProxyService] — find, message and transfer a player anywhere on the
 *   network.
 * - **plugin-player** registers [PlayerSessionQuery] — the network-wide lookup [ProxyService] falls
 *   back to for anyone who is not on this proxy.
 *
 * Consumers (plugin-chat, plugin-social) only read:
 * ```
 * val proxyService = ProxyServiceRegistry.get(ProxyService::class.java)
 * val targetId = proxyService?.resolvePlayerId(name)   // null → plugin-proxy is not installed
 * ```
 *
 * ## Two rules, and both of them bite silently
 *
 * **Never shade `plugin-proxy-api`.** Depend on it `compileOnly` and let plugin-proxy provide the
 * classes at runtime. Shade it and your plugin loads its *own* `ProxyServiceRegistry` class — a
 * different map, which nobody writes into — so every lookup returns null and every cross-proxy
 * feature quietly degrades to local-only.
 *
 * **Declare the plugin dependency.** `@Plugin(dependencies = [Dependency(id = "plugin-proxy")])` is
 * what makes Velocity initialise plugin-proxy before you; without it you may read the registry
 * before anything has registered into it. Use `optional = true` if your plugin should still load
 * (local-only) when plugin-proxy is absent.
 *
 * Providers register on `ProxyInitializeEvent` and unregister on `ProxyShutdownEvent`.
 */
object ProxyServiceRegistry {
    private val services = ConcurrentHashMap<Class<*>, Any>()

    /** Publishes [instance] under [serviceClass]. Providers call this on `ProxyInitializeEvent`. */
    fun <T : Any> register(serviceClass: Class<T>, instance: T) {
        services[serviceClass] = instance
    }

    /** The registered implementation, or null when no plugin provides it. */
    fun <T : Any> get(serviceClass: Class<T>): T? {
        return serviceClass.cast(services[serviceClass])
    }

    /** Withdraws the implementation. Providers call this on `ProxyShutdownEvent`. */
    fun <T : Any> unregister(serviceClass: Class<T>) {
        services.remove(serviceClass)
    }
}
