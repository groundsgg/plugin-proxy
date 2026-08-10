package gg.grounds.proxy.velocity.command

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import gg.grounds.proxy.velocity.motd.MotdDocument
import gg.grounds.proxy.velocity.motd.MotdFormatException
import gg.grounds.proxy.velocity.motd.MotdGgClient
import gg.grounds.proxy.velocity.motd.MotdManager
import gg.grounds.proxy.velocity.motd.MotdPlaceholders
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.ParsingException

/**
 * `/motd` — read and change the MOTD the whole network shows in the server list.
 *
 * Global, not per proxy: the change is written to service-config and every region picks it up,
 * including regions that come up afterwards. The same document is what a dashboard will edit later,
 * so nothing said here is only true in-game.
 *
 * Every path that talks to the network — the store, motd.gg — is handed to [async], because a
 * command runs on the connection's event loop and a proxy that stalls there stops answering
 * everyone. What arrives back is only ever a message.
 */
class MotdCommand(
    private val manager: MotdManager,
    private val motdGg: MotdGgClient,
    private val async: (Runnable) -> Unit,
    private val counts: () -> Counts,
) : SimpleCommand {

    /** The numbers a preview renders `{{players}}` and `{{max}}` with. */
    data class Counts(val players: Int, val maxPlayers: Int)

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean =
        invocation.source().hasPermission(PERMISSION)

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        val arguments = invocation.arguments()
        val subcommand = arguments.firstOrNull()?.lowercase()
        val rest = arguments.drop(1).joinToString(" ").trim()

        when (subcommand) {
            null,
            "" -> show(source)
            "set" -> set(source, rest)
            "import" -> import(source, rest)
            "maxplayers" -> maxPlayers(source, rest)
            "reset" -> reset(source)
            "preview" -> preview(source, rest)
            "help" -> help(source)
            else -> {
                source.sendMessage(error("Unknown subcommand '$subcommand'."))
                help(source)
            }
        }
    }

    private fun show(source: CommandSource) {
        val document = manager.current()
        if (document == null) {
            source.sendMessage(
                Component.text(
                    if (manager.loaded()) "No MOTD is set; the proxy default is being served."
                    else "The MOTD has not been read yet.",
                    NamedTextColor.YELLOW,
                )
            )
            return
        }
        source.sendMessage(Component.text("--- MOTD ---", NamedTextColor.GOLD))
        source.sendMessage(label("source", document.source ?: "/motd"))
        document.updatedBy?.let { source.sendMessage(label("changed by", it)) }
        document.updatedAt?.let { source.sendMessage(label("changed at", it)) }
        source.sendMessage(label("max players", document.maxPlayers?.toString() ?: "proxy default"))
        source.sendMessage(Component.text("raw:", NamedTextColor.GRAY))
        source.sendMessage(Component.text(document.text, NamedTextColor.WHITE))
        sendPreview(source, document.text)
    }

    private fun set(source: CommandSource, text: String) {
        if (text.isEmpty()) {
            source.sendMessage(error("Usage: /motd set <MiniMessage>"))
            return
        }
        if (!parses(source, text)) return
        warnOnExtraLines(source, text)

        val document = (manager.current() ?: MotdDocument(text)).copy(text = text, source = "/motd")
        store(source, document) {
            source.sendMessage(Component.text("MOTD updated.", NamedTextColor.GREEN))
            sendPreview(source, text)
        }
    }

    private fun import(source: CommandSource, idOrUrl: String) {
        if (idOrUrl.isEmpty()) {
            source.sendMessage(error("Usage: /motd import <motd.gg link or id>"))
            return
        }
        source.sendMessage(Component.text("Fetching from motd.gg...", NamedTextColor.GRAY))
        async {
            val imported =
                try {
                    motdGg.import(idOrUrl)
                } catch (ex: MotdFormatException) {
                    source.sendMessage(error("Import failed: ${ex.message}"))
                    return@async
                }
            if (imported.hasFavicon) {
                source.sendMessage(
                    Component.text(
                        "The motd.gg document has a server icon; it was not imported " +
                            "(the network icon is served from the CDN).",
                        NamedTextColor.YELLOW,
                    )
                )
            }
            val document =
                (manager.current() ?: MotdDocument(imported.text)).copy(
                    text = imported.text,
                    source = "motd.gg/${imported.id}",
                )
            writeAndReport(source, document) {
                source.sendMessage(
                    Component.text(
                        "MOTD imported from motd.gg/${imported.id}" +
                            (imported.name?.let { " ($it)" } ?: "") +
                            ".",
                        NamedTextColor.GREEN,
                    )
                )
                sendPreview(source, imported.text)
            }
        }
    }

    private fun maxPlayers(source: CommandSource, argument: String) {
        val document = manager.current()
        if (document == null) {
            source.sendMessage(
                error("No MOTD is set — run /motd set first; max players is stored alongside it.")
            )
            return
        }
        val value =
            when {
                argument.isEmpty() -> {
                    source.sendMessage(error("Usage: /motd maxplayers <number|clear>"))
                    return
                }
                argument.equals("clear", true) -> null
                else ->
                    argument.toIntOrNull()?.takeIf { it >= 0 }
                        ?: run {
                            source.sendMessage(error("'$argument' is not a player count."))
                            return
                        }
            }
        store(source, document.copy(maxPlayers = value)) {
            source.sendMessage(
                Component.text(
                    if (value == null) "Max players cleared; the proxy value is used again."
                    else "Max players set to $value.",
                    NamedTextColor.GREEN,
                )
            )
        }
    }

    private fun reset(source: CommandSource) {
        val actor = actorOf(source)
        async {
            try {
                val removed = manager.clear(actor)
                source.sendMessage(
                    Component.text(
                        if (removed) "MOTD removed; the proxy default is served again."
                        else "There was no MOTD to remove.",
                        NamedTextColor.GREEN,
                    )
                )
            } catch (ex: Exception) {
                source.sendMessage(error("Could not remove the MOTD: ${describe(ex)}"))
            }
        }
    }

    private fun preview(source: CommandSource, text: String) {
        val subject = text.ifEmpty { manager.current()?.text }
        if (subject == null) {
            source.sendMessage(error("Nothing to preview: no MOTD is set."))
            return
        }
        if (!parses(source, subject)) return
        sendPreview(source, subject)
    }

    private fun help(source: CommandSource) {
        source.sendMessage(Component.text("--- /motd ---", NamedTextColor.GOLD))
        listOf(
                "/motd" to "show the MOTD in use",
                "/motd set <MiniMessage>" to "change it; <newline> starts the second line",
                "/motd import <link|id>" to "take the design from motd.gg",
                "/motd maxplayers <n|clear>" to "the number right of the slash",
                "/motd preview [MiniMessage]" to "render it here without changing anything",
                "/motd reset" to "remove it and serve the proxy default",
            )
            .forEach { (usage, what) ->
                source.sendMessage(
                    Component.text("  $usage", NamedTextColor.WHITE)
                        .append(Component.text(" — $what", NamedTextColor.GRAY))
                )
            }
        source.sendMessage(
            Component.text(
                "Placeholders: " + MotdPlaceholders.TOKENS.joinToString(", ") { "{{$it}}" },
                NamedTextColor.GRAY,
            )
        )
    }

    /** Writes on a scheduler thread and reports either outcome to [source]. */
    private fun store(source: CommandSource, document: MotdDocument, onSuccess: () -> Unit) {
        async { writeAndReport(source, document, onSuccess) }
    }

    private fun writeAndReport(
        source: CommandSource,
        document: MotdDocument,
        onSuccess: () -> Unit,
    ) {
        try {
            manager.set(document, actorOf(source))
            onSuccess()
        } catch (ex: Exception) {
            source.sendMessage(error("Could not save the MOTD: ${describe(ex)}"))
        }
    }

    private fun parses(source: CommandSource, text: String): Boolean =
        try {
            MiniMessage.miniMessage().deserialize(text)
            true
        } catch (ex: ParsingException) {
            source.sendMessage(error("That is not valid MiniMessage: ${ex.message}"))
            false
        }

    private fun warnOnExtraLines(source: CommandSource, text: String) {
        val breaks = LINE_BREAK.findAll(text).count()
        if (breaks > 1) {
            source.sendMessage(
                Component.text(
                    "That is ${breaks + 1} lines; the server list shows two.",
                    NamedTextColor.YELLOW,
                )
            )
        }
    }

    private fun sendPreview(source: CommandSource, text: String) {
        val counts = counts()
        source.sendMessage(Component.text("preview:", NamedTextColor.GRAY))
        source.sendMessage(manager.preview(text, counts.players, counts.maxPlayers))
    }

    private fun label(name: String, value: String): Component =
        Component.text("  $name: ", NamedTextColor.GRAY)
            .append(Component.text(value, NamedTextColor.WHITE))

    private fun error(message: String): Component = Component.text(message, NamedTextColor.RED)

    /**
     * The store already unwrapped what service-config wrote for a human — a caller that is not
     * allowed to write says so in the problem's detail. Anything else falls back to the exception,
     * which is at least a sentence rather than a status line.
     */
    private fun describe(ex: Exception): String = ex.message ?: ex::class.java.simpleName

    private fun actorOf(source: CommandSource): String = (source as? Player)?.username ?: "console"

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        val arguments = invocation.arguments()
        return when (arguments.size) {
            0 -> SUBCOMMANDS
            1 -> SUBCOMMANDS.filter { it.startsWith(arguments[0].lowercase()) }
            2 ->
                if (arguments[0].equals("maxplayers", true)) {
                    listOf("clear").filter { it.startsWith(arguments[1].lowercase()) }
                } else {
                    emptyList()
                }
            else -> emptyList()
        }
    }

    private companion object {
        /**
         * Not one of the roles forge grants by default — an operator who should change what every
         * player sees needs it granted deliberately, the same way in-game administration is.
         */
        const val PERMISSION = "grounds.motd.manage"

        val SUBCOMMANDS = listOf("set", "import", "maxplayers", "preview", "reset", "help")

        /**
         * Every line break MiniMessage understands: both tag spellings, and the literal newline a
         * motd.gg import arrives with.
         */
        val LINE_BREAK = Regex("<newline>|<br>|\n")
    }
}
