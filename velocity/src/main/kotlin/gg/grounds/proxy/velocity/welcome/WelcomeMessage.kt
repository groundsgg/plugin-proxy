package gg.grounds.proxy.velocity.welcome

import gg.grounds.i18n.MessageKey
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
internal class WelcomeMessage
@JvmOverloads
constructor(
    private val messages: Translations,
    private val links: WelcomeLinks = WelcomeLinks(),
    private val decoratedLogoEnabled: Boolean = false,
) {
    private enum class Copy(override val id: String) : MessageKey {
        GREETING("welcome.greeting"),
        VOICE("welcome.voice"),
        INSTALL("welcome.install"),
    }

    @JvmOverloads
    fun render(
        playerName: String,
        locale: Locale,
        logoReady: Boolean,
        voiceInstalled: Boolean,
        iconsReady: Boolean = false,
    ): Component {
        val decoratedLogo = decoratedLogoEnabled && logoReady && iconsReady
        val brand =
            if (decoratedLogo)
                Component.text(WelcomeGlyphs.LOGO_TEXT)
                    .font(WelcomeGlyphs.FONT)
                    .color(TextColor.color(0xFFFFFF))
            else if (logoReady)
                Component.text(TabGlyphs.LOGO.toString())
                    .font(TabGlyphs.FONT)
                    .color(TextColor.color(0xFFFFFF))
            else Component.text("GROUNDS", GOLD, TextDecoration.BOLD)
        val result = Component.text().font(Key.key("minecraft:default"))
        // At height42/ascent41, row4 starts the mosaic at y2 and ends at y44.
        // The greeting starts at y45; the separate social row7 starts its icons at y58.
        val leadingRows =
            when {
                decoratedLogo -> if (voiceInstalled) 5 else 4
                logoReady -> if (voiceInstalled) 3 else 2
                else -> 1
            }
        repeat(leadingRows) { result.appendNewline() }
        result.append(WelcomeLayout.center(brand, logoReady)).appendNewline()
        if (logoReady && !decoratedLogo) result.appendNewline()
        result
            .append(
                WelcomeLayout.center(
                    messages.render(Copy.GREETING, locale, "player" to playerName),
                    logoReady,
                )
            )
            .appendNewline()
            .appendNewline()
        val communityGap = Component.text("   ")
        val community =
            Component.empty()
                .append(
                    communityLink(
                        WelcomeGlyphs.DISCORD,
                        "Discord",
                        links.discord,
                        iconsReady && logoReady,
                    )
                )
                .append(communityGap)
                .append(
                    communityLink(
                        WelcomeGlyphs.TIKTOK,
                        "TikTok",
                        links.tiktok,
                        iconsReady && logoReady,
                    )
                )
                .append(communityGap)
                .append(
                    communityLink(WelcomeGlyphs.SHOP, "Shop", links.shop, iconsReady && logoReady)
                )
        result.append(WelcomeLayout.center(community, logoReady))
        if (!voiceInstalled) {
            result.appendNewline()
            // The compact pack aligns the full icon bitmap above the following normal text row;
            // reserve the spare row below the copy.
            if (!decoratedLogo) result.appendNewline()
            result.append(
                WelcomeLayout.center(
                    Component.empty()
                        .append(messages.render(Copy.VOICE, locale))
                        .append(Component.text(" • ", SAND))
                        .append(link(messages.render(Copy.INSTALL, locale), INSTALL_URL)),
                    logoReady,
                )
            )
        }
        // Always retain background below the last sentence or bitmap icon row.
        return result.appendNewline().build()
    }

    private fun communityLink(
        icon: Char,
        label: String,
        url: String,
        iconsReady: Boolean,
    ): Component {
        val row = Component.text()
        if (iconsReady) {
            row.append(
                    Component.text(icon.toString())
                        .font(WelcomeGlyphs.FONT)
                        .color(TextColor.color(0xFFFFFF))
                )
                .append(Component.space())
        }
        return link(row.append(Component.text(label, GOLD)).build(), url)
    }

    private fun link(label: Component, url: String): Component =
        label.clickEvent(ClickEvent.openUrl(url)).hoverEvent(Component.text(url, SAND))

    companion object {
        // Minecraft Mini-CD: gold.primary and text.secondary, not the web UI palette.
        private val GOLD = TextColor.color(0xE7B84B)
        private val SAND = TextColor.color(0xC1B9AA)
        const val INSTALL_URL = "https://modrepo.de/minecraft/voicechat/wiki/installation"
    }
}
