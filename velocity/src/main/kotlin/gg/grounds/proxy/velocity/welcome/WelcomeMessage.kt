package gg.grounds.proxy.velocity.welcome

import gg.grounds.i18n.MessageKey
import gg.grounds.i18n.Palette
import gg.grounds.i18n.Translations
import gg.grounds.proxy.velocity.tab.TabGlyphs
import java.util.Locale
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

internal data class WelcomeLinks(
    val discord: String = "https://grnds.link/discord",
    val tiktok: String = "https://example.com/grounds/tiktok",
    val shop: String = "https://example.com/grounds/shop",
) {
    companion object {
        fun from(environment: (String) -> String? = System::getenv): WelcomeLinks {
            val defaults = WelcomeLinks()
            return WelcomeLinks(
                environment("WELCOME_DISCORD_URL")?.takeIf(String::isNotBlank) ?: defaults.discord,
                environment("WELCOME_TIKTOK_URL")?.takeIf(String::isNotBlank) ?: defaults.tiktok,
                environment("WELCOME_SHOP_URL")?.takeIf(String::isNotBlank) ?: defaults.shop,
            )
        }
    }
}

/** One branded block: every link owns its click event, so it cannot leak onto the next line. */
internal class WelcomeMessage(
    private val messages: Translations,
    private val links: WelcomeLinks = WelcomeLinks(),
) {
    private enum class Copy(override val id: String) : MessageKey {
        GREETING("welcome.greeting"),
        VOICE("welcome.voice"),
        INSTALL("welcome.install"),
    }

    fun render(
        playerName: String,
        locale: Locale,
        logoReady: Boolean,
        voiceInstalled: Boolean,
    ): Component {
        val brand =
            if (logoReady)
                Component.text(TabGlyphs.LOGO.toString())
                    .font(TabGlyphs.FONT)
                    .color(TextColor.color(0xFFFFFF))
            else Component.text("GROUNDS", Palette.TEXT, TextDecoration.BOLD)
        val result = Component.text().font(Key.key("minecraft:default"))
        // Reserve room around the 26px wordmark while fitting the default ten-line closed chat.
        repeat(if (logoReady) 2 else 1) { result.appendNewline() }
        result.append(brand).appendNewline()
        if (logoReady) result.appendNewline()
        result
            .append(messages.render(Copy.GREETING, locale, "player" to playerName))
            .appendNewline()
            .append(communityLink("🎮", "Discord", links.discord))
            .appendNewline()
            .append(communityLink("🎵", "TikTok", links.tiktok))
            .appendNewline()
            .append(communityLink("🛒", "Shop", links.shop))
        if (!voiceInstalled) {
            result
                .appendNewline()
                .append(messages.render(Copy.VOICE, locale))
                .appendNewline()
                .append(link(messages.render(Copy.INSTALL, locale), INSTALL_URL))
        }
        return result.build()
    }

    private fun communityLink(icon: String, label: String, url: String): Component =
        link(Component.text("$icon $label", Palette.ACCENT), url)

    private fun link(label: Component, url: String): Component =
        label
            .clickEvent(ClickEvent.openUrl(url))
            .hoverEvent(Component.text(url, Palette.TEXT_MUTED))

    companion object {
        const val INSTALL_URL = "https://modrepo.de/minecraft/voicechat/wiki/installation"
    }
}
