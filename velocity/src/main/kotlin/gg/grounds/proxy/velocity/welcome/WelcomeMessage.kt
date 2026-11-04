package gg.grounds.proxy.velocity.welcome

import gg.grounds.i18n.MessageKey
import gg.grounds.i18n.Palette
import gg.grounds.i18n.Translations
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
        val brand = Component.text("GROUNDS", Palette.TEXT, TextDecoration.BOLD)
        val result = Component.text().appendNewline()
        if (logoReady) {
            result
                .append(
                    Component.text("\uE100")
                        .font(Key.key("grounds:chat"))
                        .color(TextColor.color(0xFFFFFF))
                )
                .append(Component.space())
        }
        result
            .append(brand)
            .appendNewline()
            .append(messages.render(Copy.GREETING, locale, "player" to playerName))
            .appendNewline()
            .appendNewline()
            .append(link(Component.text("Discord", Palette.ACCENT), links.discord))
            .append(Component.text(" · ", Palette.TEXT_FAINT))
            .append(link(Component.text("TikTok", Palette.ACCENT), links.tiktok))
            .append(Component.text(" · ", Palette.TEXT_FAINT))
            .append(link(Component.text("Shop", Palette.ACCENT), links.shop))
        if (!voiceInstalled) {
            result
                .appendNewline()
                .appendNewline()
                .append(messages.render(Copy.VOICE, locale))
                .appendNewline()
                .append(link(messages.render(Copy.INSTALL, locale), INSTALL_URL))
        }
        return result.appendNewline().build()
    }

    private fun link(label: Component, url: String): Component =
        label
            .clickEvent(ClickEvent.openUrl(url))
            .hoverEvent(Component.text(url, Palette.TEXT_MUTED))

    companion object {
        const val INSTALL_URL = "https://modrepo.de/minecraft/voicechat/wiki/installation"
    }
}
