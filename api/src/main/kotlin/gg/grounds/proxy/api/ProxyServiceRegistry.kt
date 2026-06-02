package gg.grounds.proxy.api

import java.util.concurrent.ConcurrentHashMap

object ProxyServiceRegistry {
    private val services = ConcurrentHashMap<Class<*>, Any>()

    fun <T : Any> register(serviceClass: Class<T>, instance: T) {
        services[serviceClass] = instance
    }

    fun <T : Any> get(serviceClass: Class<T>): T? {
        return serviceClass.cast(services[serviceClass])
    }

    fun <T : Any> unregister(serviceClass: Class<T>) {
        services.remove(serviceClass)
    }
}
