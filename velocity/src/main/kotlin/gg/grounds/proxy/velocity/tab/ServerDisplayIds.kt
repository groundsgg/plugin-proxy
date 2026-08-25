package gg.grounds.proxy.velocity.tab

object ServerDisplayIds {
    fun idOf(serverName: String): String {
        val id = serverName.substringAfterLast('-')
        return if (id.isEmpty()) serverName else id
    }
}
