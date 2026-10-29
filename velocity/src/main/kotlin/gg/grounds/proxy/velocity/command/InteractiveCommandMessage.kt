package gg.grounds.proxy.velocity.command

import gg.grounds.i18n.MessageKey
import gg.grounds.i18n.Translations
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent

internal data class CommandAction(val command: String, val runsImmediately: Boolean) {
    companion object {
        fun run(command: String) = CommandAction(command, runsImmediately = true)

        fun suggest(command: String) = CommandAction(command, runsImmediately = false)
    }
}

/** Makes a localized action label discoverable without showing its command syntax in chat. */
internal fun interactiveCommand(
    message: Component,
    action: CommandAction,
    hint: String,
): Component =
    message
        .clickEvent(
            if (action.runsImmediately) ClickEvent.runCommand(action.command)
            else ClickEvent.suggestCommand(action.command)
        )
        .hoverEvent(HoverEvent.showText(Component.text(hint)))

/**
 * Sends an interactive localized system message through the normal renderer, keeping its category,
 * level, prefix, and palette intact.
 */
internal fun Translations.sendInteractive(
    audience: Audience,
    key: MessageKey,
    action: CommandAction,
    hint: String,
    vararg arguments: Pair<String, Any>,
) {
    sendRendered(audience, key, interactiveCommand(render(key, audience, *arguments), action, hint))
}

/**
 * Read-only MOTD help can run immediately; edits and the destructive reset stay in the input box.
 */
internal fun motdHelpAction(usage: String): CommandAction {
    val command = usage.substringBefore(" <").substringBefore(" [")
    return when (command) {
        "/motd",
        "/motd preview" -> CommandAction.run(command)
        else -> CommandAction.suggest(command + if ('<' in usage) " " else "")
    }
}
