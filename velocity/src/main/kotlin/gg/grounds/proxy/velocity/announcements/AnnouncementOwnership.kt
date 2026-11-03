package gg.grounds.proxy.velocity.announcements

enum class AnnouncementOwnership {
    LEGACY,
    DISABLED,
    PROXY;

    companion object {
        fun parse(value: String?): AnnouncementOwnership? =
            when (value) {
                null,
                "legacy" -> LEGACY
                "disabled" -> DISABLED
                "proxy" -> PROXY
                else -> null
            }
    }
}
