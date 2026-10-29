package gg.grounds.proxy.velocity.command

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import gg.grounds.i18n.Translations
import gg.grounds.proxy.velocity.ProxySystemMessage
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
    private val messages: Translations,
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
                messages.send(
                    source,
                    ProxySystemMessage.MOTD_UNKNOWN_SUBCOMMAND,
                    "subcommand" to subcommand,
                )
                help(source)
            }
        }
    }

    private fun show(source: CommandSource) {
        val document = manager.current()
        if (document == null) {
            messages.send(
                source,
                if (manager.loaded()) ProxySystemMessage.MOTD_NOT_SET
                else ProxySystemMessage.MOTD_NOT_READ,
            )
            return
        }
        messages.send(source, ProxySystemMessage.MOTD_HEADER)
        messages.send(
            source,
            ProxySystemMessage.MOTD_SOURCE,
            "source" to (document.source ?: "/motd"),
        )
        document.updatedBy?.let {
            messages.send(source, ProxySystemMessage.MOTD_CHANGED_BY, "actor" to it)
        }
        document.updatedAt?.let {
            messages.send(source, ProxySystemMessage.MOTD_CHANGED_AT, "time" to it)
        }
        document.maxPlayers?.let {
            messages.send(source, ProxySystemMessage.MOTD_MAX_PLAYERS, "count" to it)
        } ?: messages.send(source, ProxySystemMessage.MOTD_MAX_PLAYERS_DEFAULT)
        messages.send(source, ProxySystemMessage.MOTD_RAW)
        source.sendMessage(Component.text(document.text, NamedTextColor.WHITE))
        sendPreview(source, document.text)
    }

    private fun set(source: CommandSource, text: String) {
        if (text.isEmpty()) {
            messages.sendInteractive(
                source,
                ProxySystemMessage.MOTD_USAGE_SET,
                CommandAction.suggest("/motd set "),
                "/motd set <MiniMessage>",
            )
            return
        }
        if (!parses(source, text)) return
        warnOnExtraLines(source, text)

        val document = (manager.current() ?: MotdDocument(text)).copy(text = text, source = "/motd")
        store(source, document) {
            messages.send(source, ProxySystemMessage.MOTD_UPDATED)
            sendPreview(source, text)
        }
    }

    private fun import(source: CommandSource, idOrUrl: String) {
        if (idOrUrl.isEmpty()) {
            messages.sendInteractive(
                source,
                ProxySystemMessage.MOTD_USAGE_IMPORT,
                CommandAction.suggest("/motd import "),
                "/motd import <motd.gg link or id>",
            )
            return
        }
        messages.send(source, ProxySystemMessage.MOTD_FETCHING)
        async {
            val imported =
                try {
                    motdGg.import(idOrUrl)
                } catch (ex: MotdFormatException) {
                    messages.send(
                        source,
                        ProxySystemMessage.MOTD_IMPORT_FAILED,
                        "error" to (ex.message ?: ex::class.java.simpleName),
                    )
                    return@async
                }
            if (imported.hasFavicon) {
                messages.send(source, ProxySystemMessage.MOTD_ICON_SKIPPED)
            }
            val document =
                (manager.current() ?: MotdDocument(imported.text)).copy(
                    text = imported.text,
                    source = "motd.gg/${imported.id}",
                )
            writeAndReport(source, document) {
                messages.send(
                    source,
                    if (imported.name == null) ProxySystemMessage.MOTD_IMPORTED
                    else ProxySystemMessage.MOTD_IMPORTED_NAMED,
                    "id" to imported.id,
                    "name" to imported.name.orEmpty(),
                )
                sendPreview(source, imported.text)
            }
        }
    }

    private fun maxPlayers(source: CommandSource, argument: String) {
        val document = manager.current()
        if (document == null) {
            messages.sendInteractive(
                source,
                ProxySystemMessage.MOTD_REQUIRED_FOR_MAX_PLAYERS,
                CommandAction.suggest("/motd set "),
                "/motd set <MiniMessage>",
            )
            return
        }
        val value =
            when {
                argument.isEmpty() -> {
                    messages.sendInteractive(
                        source,
                        ProxySystemMessage.MOTD_USAGE_MAX_PLAYERS,
                        CommandAction.suggest("/motd maxplayers "),
                        "/motd maxplayers <number|clear>",
                    )
                    return
                }
                argument.equals("clear", true) -> null
                else ->
                    argument.toIntOrNull()?.takeIf { it >= 0 }
                        ?: run {
                            messages.send(
                                source,
                                ProxySystemMessage.MOTD_INVALID_PLAYER_COUNT,
                                "value" to argument,
                            )
                            return
                        }
            }
        store(source, document.withMaximum(value)) {
            messages.send(
                source,
                if (value == null) ProxySystemMessage.MOTD_MAX_PLAYERS_CLEARED
                else ProxySystemMessage.MOTD_MAX_PLAYERS_SET,
                "count" to (value ?: 0),
            )
        }
    }

    private fun reset(source: CommandSource) {
        val actor = actorOf(source)
        async {
            try {
                val removed = manager.clear(actor)
                messages.send(
                    source,
                    if (removed) ProxySystemMessage.MOTD_REMOVED
                    else ProxySystemMessage.MOTD_NOTHING_TO_REMOVE,
                )
            } catch (ex: Exception) {
                messages.send(
                    source,
                    ProxySystemMessage.MOTD_REMOVE_FAILED,
                    "error" to describe(ex),
                )
            }
        }
    }

    private fun preview(source: CommandSource, text: String) {
        val subject = text.ifEmpty { manager.current()?.text }
        if (subject == null) {
            messages.send(source, ProxySystemMessage.MOTD_NOTHING_TO_PREVIEW)
            return
        }
        if (!parses(source, subject)) return
        sendPreview(source, subject)
    }

    private fun help(source: CommandSource) {
        messages.send(source, ProxySystemMessage.MOTD_HELP_HEADER)
        listOf(
                ProxySystemMessage.MOTD_HELP_SHOW to "/motd",
                ProxySystemMessage.MOTD_HELP_SET to "/motd set <MiniMessage>",
                ProxySystemMessage.MOTD_HELP_IMPORT to "/motd import <link|id>",
                ProxySystemMessage.MOTD_HELP_MAX_PLAYERS to "/motd maxplayers <n|clear>",
                ProxySystemMessage.MOTD_HELP_PREVIEW to "/motd preview [MiniMessage]",
                ProxySystemMessage.MOTD_HELP_RESET to "/motd reset",
            )
            .forEach { (key, usage) ->
                messages.sendInteractive(source, key, motdHelpAction(usage), usage)
            }
        messages.send(
            source,
            ProxySystemMessage.MOTD_PLACEHOLDERS,
            "placeholders" to MotdPlaceholders.TOKENS.joinToString(", ") { "{{$it}}" },
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
            messages.send(source, ProxySystemMessage.MOTD_SAVE_FAILED, "error" to describe(ex))
        }
    }

    private fun parses(source: CommandSource, text: String): Boolean =
        try {
            MiniMessage.miniMessage().deserialize(text)
            true
        } catch (ex: ParsingException) {
            messages.send(
                source,
                ProxySystemMessage.MOTD_INVALID_MINI_MESSAGE,
                "error" to (ex.message ?: ex::class.java.simpleName),
            )
            false
        }

    private fun warnOnExtraLines(source: CommandSource, text: String) {
        val breaks = LINE_BREAK.findAll(text).count()
        if (breaks > 1) {
            messages.send(source, ProxySystemMessage.MOTD_EXTRA_LINES, "count" to (breaks + 1))
        }
    }

    private fun sendPreview(source: CommandSource, text: String) {
        val counts = counts()
        messages.send(source, ProxySystemMessage.MOTD_PREVIEW)
        source.sendMessage(manager.preview(text, counts.players, counts.maxPlayers))
    }

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
