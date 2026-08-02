package gg.grounds.proxy.velocity.tab

import gg.grounds.i18n.MessageKey

/**
 * Player-facing text owned by the proxy.
 *
 * Rendered with `render`, not `send`: the tab list is not chat, so it carries no `[Grounds]` tag —
 * the header already says whose network this is.
 */
enum class ProxyMessage(override val id: String) : MessageKey {
    TAB_HEADER("tab.header"),
    TAB_FOOTER("tab.footer"),
}
