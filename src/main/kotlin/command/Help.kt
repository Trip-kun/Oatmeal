package tech.trip_kun.sinon.command

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.trip_kun.sinon.exception.CommandExitException
import tech.trip_kun.sinon.listeners.getCommandListener

class Help(private val jda: JDA) : Command() {
    init {
        val name = "help"
        val description = "Show all available commands"
        addArgument(Argument(name, description, true, ArgumentType.COMMAND, null))
        addArgument(Argument("page", "Page number", false, ArgumentType.UINT_OVER_ZERO, null))
        addArgument(Argument("command", "Command to show help", false, ArgumentType.WORD, null))
        initialize(jda)
    }

    override fun getCategory(): CommandCategory {
        return CommandCategory.ESSENTIAL
    }

    override suspend fun handler(event: MessageReceivedEvent) {
        val arguments = parseArguments(event)
        var embedBuilder: EmbedBuilder? = null
        if (arguments.isEmpty()) {
            embedBuilder = commonWork(null, null)
        }
        if (arguments.size == 1) {
            val command = arguments[0].getStringValue()
            if (command == null) {
                val page = arguments[0].getIntValue()
                embedBuilder = commonWork(null, page)
            } else {
                embedBuilder = commonWork(command, null)
            }
        }
        if (arguments.size == 2) {
            val command = arguments[1].getStringValue()
            val page = arguments[0].getIntValue()
            embedBuilder = commonWork(command, page)
        }
        if (embedBuilder == null) {
            throw CommandExitException("Invalid arguments")
        }

        event.channel.sendMessageEmbeds(embedBuilder.build()).await()
    }

    override suspend fun handler(event: SlashCommandInteractionEvent) {
        val arguments = parseArguments(event)
        var embedBuilder: EmbedBuilder? = null
        if (arguments.isEmpty()) {
            embedBuilder = commonWork(null, null)
        }
        if (arguments.size == 1) {
            val command = arguments[0].getStringValue()
            if (command == null) {
                val page = arguments[0].getIntValue()
                embedBuilder = commonWork(null, page)
            } else {
                embedBuilder = commonWork(command, null)
            }
        }
        if (arguments.size == 2) {
            val command = arguments[1].getStringValue()
            val page = arguments[0].getIntValue()
            embedBuilder = commonWork(command, page)
        }
        if (embedBuilder == null) {
            throw CommandExitException("Invalid arguments")
        }

        event.hook.sendMessageEmbeds(embedBuilder.build()).await()
    }
    private suspend fun commonWork(command: String?, page: Int?): EmbedBuilder {
        if (command != null && page != null && command.isNotBlank()) {
            // Exit, as we can't show help for specific command and page at the same time
            throw CommandExitException("Can't show help for specific command and page at the same time")
        } else if (!command.isNullOrBlank()) {
            // Show help for specific command
            val embedBuilder = EmbedBuilder().setTitle("Help for $command")
            val foundCommand = getCommandListener().getCommands().filter { it.key == command }
            foundCommand.ifEmpty {
                throw CommandExitException("Command not found")
            }
            foundCommand.forEach { search ->
                val arguments = search.value.getArguments()
                for (argument in arguments) {
                    if (argument.getName() == command) {
                        embedBuilder.setDescription(argument.getDescription())
                        continue
                    }
                    val choices = argument.getChoices()
                    val choicesString = choices?.joinToString(", ") ?: ""
                    val required = if (argument.getRequired()) {
                        "Required"
                    } else {
                        "Optional"
                    }
                    embedBuilder.addField(
                        "Option: " + argument.getName(),
                        "${argument.getDescription()}\nType: ${argument.getType()}\nChoices: $choicesString\n$required",
                        false
                    )
                }
                return embedBuilder
            }
            return embedBuilder // Should never reach here
        } else if (page != null) {
            // Show help for specific page
            val embedBuilder = EmbedBuilder().setTitle("Help for page $page")
            for (search in getCommandListener().getCommands()) {
                val category = search.value.getCategory()
                if (category.ordinal == page - 1) {
                    embedBuilder.addField(search.key, search.value.getArguments().first().getDescription(), false)
                }

            }
            return embedBuilder
        } else {
            // Show all available pages
            val embedBuilder = EmbedBuilder().setTitle("Available Categories")
            for (category in CommandCategory.entries) {
                embedBuilder.addField("Page " + category.ordinal.inc().toString(), category.name, false)
            }
            return embedBuilder
        }
    }
}