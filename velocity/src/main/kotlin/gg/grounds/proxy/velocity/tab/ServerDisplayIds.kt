package gg.grounds.proxy.velocity.tab

object ServerDisplayIds {
    /** A human-readable position among the currently registered servers of the same kind. */
    fun numberOf(serverName: String, peers: Collection<String>): Int =
        (peers + serverName).distinct().sorted().indexOf(serverName) + 1

    fun idOf(serverName: String): String {
        val id = serverName.substringAfterLast('-')
        return if (id.isEmpty()) serverName else id
    }
}
