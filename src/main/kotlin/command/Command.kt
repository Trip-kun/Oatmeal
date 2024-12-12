package tech.trip_kun.sinon.command

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import tech.trip_kun.sinon.exception.CommandExitException
import tech.trip_kun.sinon.exception.CommandInitializationException
import tech.trip_kun.sinon.getConfig

abstract class Command {
    private lateinit var category: CommandCategory
    private var initialized: Boolean = false
    private val arguments: ArrayList<Argument> = ArrayList()
    private val captureRegex = Regex("""(['"])((?:\\\1|.)+?)\1|([^\s"']+)""")

    /**
     * Initialize the command with the JDA instance
     * This really shouldn't be called directly, but rather through CommandListener which handles the exception and command loading
     * @param jda The JDA instance to initialize the command with
     * @throws CommandInitializationException If the command is already initialized, or if the command is missing arguments
     */
    protected fun initialize(jda: JDA) {
        if (initialized) {
            val commandName = getFirstArgument()?.getName()
            throw CommandInitializationException(
                "Command " + (commandName ?: "MALFORMED_COMMAND") + " already initialized"
            )
        }
        val firstArgument = getFirstArgument()
            ?: throw CommandInitializationException("Command must have at least one argument")
        val slashCommandData = Commands.slash(firstArgument.getName(), firstArgument.getDescription())
        arguments.forEach {
            addOption(slashCommandData, it)
        }
        jda.upsertCommand(slashCommandData).queue()
        category = getCategory()
        initialized = true
    }

    /**
     * Add an argument to the command
     * @param argument The argument to add
     * @throws CommandInitializationException If the command is already initialized, or if the argument is invalid
     */
    protected fun addArgument(argument: Argument) {
        if (initialized) {
            throw CommandInitializationException("Cannot add argument to initialized command")
        }
        if (arguments.isEmpty() && argument.getType() != ArgumentType.COMMAND) {
            throw CommandInitializationException("First argument must be of type COMMAND")
        }
        if (arguments.stream().anyMatch { it.getName() == argument.getName() }) {
            throw CommandInitializationException("Argument with name " + argument.getName() + " already exists")
        }
        if (arguments.isNotEmpty() && argument.getType() == ArgumentType.COMMAND) {
            throw CommandInitializationException("Command argument must be the first argument")
        }
        if (arguments.size >= 26) { // 25 arguments + 1 command
            throw CommandInitializationException("Cannot have more than 25 arguments")
        }
        if (arguments.stream().anyMatch { !it.getRequired() } && argument.getRequired()) {
            throw CommandInitializationException("Cannot have optional argument after required argument")
        }
        if (argument.getType() == ArgumentType.SUBCOMMAND && arguments.stream()
                .anyMatch { it.getType() != ArgumentType.SUBCOMMAND && it.getType() != ArgumentType.COMMAND }
        ) {
            throw CommandInitializationException("Subcommand arguments must be first")
        }
        arguments.add(argument)
    }

    private fun getFirstArgument(): Argument? {
        return arguments.firstOrNull()
    }

    fun getArguments(): List<Argument> {
        return arguments
    }

    fun getName(): String {
        return getFirstArgument()?.getName() ?: "MALFORMED_COMMAND"
    }

    protected fun parseArguments(event: SlashCommandInteractionEvent): List<ParsedArgument> {
        val roles = ArrayList<Long>()
        val users = ArrayList<Long>()
        val channels = ArrayList<Long>()
        val attachments = ArrayList<Message.Attachment>()
        var content = ""
        var first = true
        event.options.forEach {
            when (it.type) {
                OptionType.ROLE -> roles.add(it.asRole.idLong)
                OptionType.USER -> users.add(it.asUser.idLong)
                OptionType.CHANNEL -> channels.add(it.asChannel.idLong)
                OptionType.ATTACHMENT -> attachments.add(it.asAttachment)
                else -> {
                    // Do nothing
                }
            }
            if (it.type != OptionType.ATTACHMENT) {
                val str = when (it.type) {
                    OptionType.STRING -> it.asString
                    OptionType.INTEGER -> it.asLong.toString()
                    OptionType.BOOLEAN -> it.asBoolean.toString()
                    OptionType.NUMBER -> it.asDouble.toString()
                    OptionType.USER -> it.asUser.asMention
                    OptionType.CHANNEL -> it.asChannel.asMention
                    OptionType.ROLE -> it.asRole.asMention
                    OptionType.SUB_COMMAND -> it.name
                    OptionType.SUB_COMMAND_GROUP -> it.name
                    OptionType.MENTIONABLE -> it.asMentionable.asMention
                    else -> ""
                }
                if (first) {
                    first = false
                    content += "\""
                    content += str
                    content += "\""
                } else {
                    content += " \""
                    content += str
                    content += "\""
                }
            }
        }
        return parseArguments(content, attachments, roles, users, channels)
    }

    protected fun parseArguments(event: MessageReceivedEvent): List<ParsedArgument> {
        val roles = ArrayList<Long>()
        val users = ArrayList<Long>()
        val channels = ArrayList<Long>()
        for (mentionedRole in event.message.mentions.roles) {
            roles.add(mentionedRole.idLong)
        }
        for (mentionedUser in event.message.mentions.users) {
            users.add(mentionedUser.idLong)
        }
        for (mentionedChannel in event.message.mentions.channels) {
            channels.add(mentionedChannel.idLong)
        }
        val index = getConfig().discordSettings.prefix.length + 1 + (getFirstArgument()?.getName()?.length ?: 0)
        return parseArguments(
            event.message.contentRaw.substring(if (index > event.message.contentRaw.length) index - 1 else index),
            event.message.attachments,
            roles,
            users,
            channels
        )
    }

    private fun stripSurroundingQuotes(text: String): String {
        if (text.length < 2) {
            return text
        }
        // Check for both single and double quotes, and remove them if they are present on either end
        var start = 0
        var end = text.length - 1
        if (text[start] == '"' || text[start] == '\'') {
            start++
        }
        if (text[end] == '"' || text[end] == '\'') {
            end--
        }
        return text.substring(start, end + 1)

    }

    private fun parseArguments(
        messageContent: String,
        attachments: Collection<Message.Attachment>,
        mentionedRoles: Collection<Long>,
        mentionedUsers: Collection<Long>,
        mentionedChannels: Collection<Long>
    ): List<ParsedArgument> {
        val parsedArguments = ArrayList<ParsedArgument>()
        val partsR = captureRegex.findAll(messageContent)
        val parts = partsR.map { stripSurroundingQuotes(it.value) }.toList().filter { it.isNotBlank() }
        var index = 0
        if (parts.size == 1 && parts[0].isEmpty()) {
            index = 1
        }
        var numAttachments = 0
        var numRoles = 0
        var numUsers = 0
        var numChannels = 0
        val attachmentsArray = attachments.toTypedArray()
        val mentionedRolesArray = mentionedRoles.toTypedArray()
        val mentionedUsersArray = mentionedUsers.toTypedArray()
        val mentionedChannelsArray = mentionedChannels.toTypedArray()
        arguments.forEach {
            try {
                when (it.getType()) {
                    ArgumentType.ATTACHMENT -> {
                        if (attachments.size < ++numAttachments) {
                            if (it.getRequired()) {
                                throw CommandExitException("Argument attachment " + it.getName() + " is required")
                            }
                        } else {
                            parsedArguments.add(
                                ParsedArgument(
                                    ArgumentType.ATTACHMENT,
                                    attachmentsArray[numAttachments - 1]
                                )
                            )
                        }
                    }

                    ArgumentType.ROLE -> {
                        if (mentionedRoles.size < ++numRoles) {
                            if (it.getRequired()) {
                                throw CommandExitException("Argument role " + it.getName() + " is required")
                            }
                        } else {
                            parsedArguments.add(ParsedArgument(ArgumentType.ROLE, mentionedRolesArray[numRoles - 1]))
                            index++
                        }
                    }

                    ArgumentType.USER -> {
                        if (mentionedUsers.size < ++numUsers) {
                            if (it.getRequired()) {
                                throw CommandExitException("Argument user " + it.getName() + " is required")
                            }
                        } else {
                            parsedArguments.add(ParsedArgument(ArgumentType.USER, mentionedUsersArray[numUsers - 1]))
                            index++
                        }
                    }

                    ArgumentType.CHANNEL -> {
                        if (mentionedChannels.size < ++numChannels) {
                            if (it.getRequired()) {
                                throw CommandExitException("Argument channel " + it.getName() + " is required")
                            }
                        } else {
                            parsedArguments.add(
                                ParsedArgument(
                                    ArgumentType.CHANNEL,
                                    mentionedChannelsArray[numChannels - 1]
                                )
                            )
                            index++
                        }
                    }

                    ArgumentType.INTEGER -> {
                        if (index >= parts.size) {
                            if (it.getRequired()) {
                                throw CommandExitException("Argument integer " + it.getName() + " is required")
                            }
                        } else {
                            parsedArguments.add(ParsedArgument(ArgumentType.INTEGER, parts[index].toInt()))
                            index++
                        }
                    }

                    ArgumentType.UINT -> {
                        if (index >= parts.size) {
                            if (it.getRequired()) {
                                throw CommandExitException("Argument unsigned integer " + it.getName() + " is required")
                            }
                        } else {
                            val value = parts[index].toInt()
                            if (value < 0) {
                                throw CommandExitException("Argument unsigned integer " + it.getName() + " must be positive")
                            }
                            parsedArguments.add(ParsedArgument(ArgumentType.UINT, value))
                            index++
                        }
                    }

                    ArgumentType.UINT_OVER_ZERO -> {
                        if (index >= parts.size) {
                            if (it.getRequired()) {
                                throw CommandExitException("Argument unsigned integer over zero " + it.getName() + " is required")
                            }
                        } else {
                            val value = parts[index].toInt()
                            if (value <= 0) {
                                throw CommandExitException("Argument unsigned integer over zero " + it.getName() + " must be positive")
                            }
                            parsedArguments.add(ParsedArgument(ArgumentType.UINT_OVER_ZERO, value))
                            index++
                        }
                    }

                    ArgumentType.DECIMAL -> {
                        if (index >= parts.size) {
                            if (it.getRequired()) {
                                throw CommandExitException("Argument decimal " + it.getName() + " is required")
                            }
                        } else {
                            parsedArguments.add(ParsedArgument(ArgumentType.DECIMAL, parts[index].toDouble()))
                            index++
                        }
                    }

                    ArgumentType.UDECIMAL -> {
                        if (index >= parts.size) {
                            if (it.getRequired()) {
                                throw CommandExitException("Argument unsigned decimal " + it.getName() + " is required")
                            }
                        } else {
                            val value = parts[index].toDouble()
                            if (value < 0) {
                                throw CommandExitException("Argument unsigned decimal " + it.getName() + " must be positive")
                            }
                            parsedArguments.add(ParsedArgument(ArgumentType.UDECIMAL, value))
                            index++
                        }
                    }

                    ArgumentType.UDECIMAL_OVER_ZERO -> {
                        if (index >= parts.size) {
                            if (it.getRequired()) {
                                throw CommandExitException("Argument unsigned decimal over zero " + it.getName() + " is required")
                            }
                        } else {
                            val value = parts[index].toDouble()
                            if (value <= 0) {
                                throw CommandExitException("Argument unsigned decimal over zero " + it.getName() + " must be positive")
                            }
                            parsedArguments.add(ParsedArgument(ArgumentType.UDECIMAL_OVER_ZERO, value))
                            index++
                        }
                    }

                    ArgumentType.BOOLEAN -> {
                        if (index >= parts.size) {
                            if (it.getRequired()) {
                                throw CommandExitException("Argument boolean " + it.getName() + " is required")
                            }
                        } else {
                            if (!parts[index].equals("true", ignoreCase = true) && !parts[index].equals(
                                    "false",
                                    ignoreCase = true
                                )
                            ) {
                                throw CommandExitException("Argument boolean " + it.getName() + " must be true or false")
                            }
                            parsedArguments.add(ParsedArgument(ArgumentType.BOOLEAN, parts[index].toBoolean()))
                            index++
                        }
                    }

                    ArgumentType.WORD, ArgumentType.SUBCOMMAND -> {
                        if (index >= parts.size) {
                            if (it.getRequired()) {
                                if (it.getType() == ArgumentType.SUBCOMMAND) {
                                    throw CommandExitException("Argument subcommand " + it.getName() + " is required")
                                } else {
                                    throw CommandExitException("Argument word " + it.getName() + " is required")
                                }
                            }
                            parsedArguments.add(ParsedArgument(ArgumentType.WORD, ""))
                        } else {
                            if (parts[index].contains("\\s".toRegex())) {
                                if (it.getType() == ArgumentType.SUBCOMMAND) {
                                    throw CommandExitException("Argument subcommand " + it.getName() + " must not contain spaces (do not use quotes for this argument)")
                                } else {
                                    throw CommandExitException("Argument word " + it.getName() + " must not contain spaces (do not use quotes for this argument)")
                                }
                            }
                            if (it.getRequired() && parts[index].isEmpty()) {
                                if (it.getType() == ArgumentType.SUBCOMMAND) {
                                    throw CommandExitException("Argument subcommand " + it.getName() + " is required")
                                } else {
                                    throw CommandExitException("Argument word " + it.getName() + " is required")
                                }
                            }
                            if (!it.getRequired() && parts[index].isEmpty()) {
                                parsedArguments.add(ParsedArgument(it.getType(), ""))
                            } else {
                                if (it.getType() == ArgumentType.SUBCOMMAND) {
                                    if (it.getChoices()?.contains(parts[index]) == false) {
                                        throw CommandExitException("Argument subcommand " + it.getName() + " must be one of " + it.getChoices())
                                    }
                                } else {
                                    parsedArguments.add(ParsedArgument(it.getType(), parts[index]))
                                    index++
                                }
                            }
                        }
                    }

                    ArgumentType.TEXT -> { // Now treated normal as we have a regex to split the message including quotes for text
                        if (index >= parts.size) {
                            if (it.getRequired()) {
                                throw CommandExitException("Argument text " + it.getName() + " is required")
                            }
                        } else {
                            parsedArguments.add(ParsedArgument(ArgumentType.TEXT, parts[index]))
                            index++
                        }
                    }

                    else -> {
                        // Do nothing, this only happens with COMMAND, which is handled first before this loop
                    }
                }
            } catch (e: NumberFormatException) {
                if (it.getRequired()) {
                    throw CommandExitException("Argument " + it.getType() + " " + it.getName() + " must be a number")
                }
            }
        }
        return parsedArguments
    }

    /**
     * Get the category of the command
     * Notably, this is only called once, so the value cannot change.
     * This is used for help commands to categorize commands
     * @return The category of the command
     */
    abstract fun getCategory(): CommandCategory

    /**
     * Handle a message received event
     * @see handler(event: SlashCommandInteractionEvent)
     * @param event The message received event to handle
     */
    abstract suspend fun handler(event: MessageReceivedEvent)
    /**
     * Handle a slash command interaction event
     * @see handler(event: MessageReceivedEvent)
     * @param event The slash command interaction event to handle
     */
    abstract suspend fun handler(event: SlashCommandInteractionEvent)
}

enum class CommandCategory {
    ESSENTIAL,
    MODERATION,
    SETTINGS,
    UTILITY
}

//*
// * Adds an option to the slash command data
// * @param slashCommandData The slash command data to add the option to
// * @param argument The argument to add as an option
// */
private fun addOption(slashCommandData: SlashCommandData, argument: Argument) {
    if (argument.getName().length > 32) {
        throw CommandInitializationException("Argument name " + argument.getName() + " is too long")
    }
    if (argument.getDescription().length > 100) {
        throw CommandInitializationException("Argument description " + argument.getDescription() + " is too long")
    }
    when (argument.getType()) {
        ArgumentType.INTEGER, ArgumentType.UINT, ArgumentType.UINT_OVER_ZERO -> {
            slashCommandData.addOption(
                OptionType.INTEGER,
                argument.getName(),
                argument.getDescription(),
                argument.getRequired()
            )
        }

        ArgumentType.WORD, ArgumentType.TEXT -> {
            val optionData = OptionData(
                OptionType.STRING,
                argument.getName(),
                argument.getDescription(),
                argument.getRequired(),
                false
            )
            argument.getChoices()?.forEach { choice ->
                optionData.addChoice(choice, choice)
            }
            slashCommandData.addOptions(optionData)
        }

        ArgumentType.BOOLEAN -> {
            slashCommandData.addOption(
                OptionType.BOOLEAN,
                argument.getName(),
                argument.getDescription(),
                argument.getRequired()
            )
        }

        ArgumentType.DECIMAL, ArgumentType.UDECIMAL, ArgumentType.UDECIMAL_OVER_ZERO -> {
            slashCommandData.addOption(
                OptionType.NUMBER,
                argument.getName(),
                argument.getDescription(),
                argument.getRequired()
            )
        }

        ArgumentType.USER -> {
            slashCommandData.addOption(
                OptionType.USER,
                argument.getName(),
                argument.getDescription(),
                argument.getRequired()
            )
        }

        ArgumentType.CHANNEL -> {
            slashCommandData.addOption(
                OptionType.CHANNEL,
                argument.getName(),
                argument.getDescription(),
                argument.getRequired()
            )
        }

        ArgumentType.ROLE -> {
            slashCommandData.addOption(
                OptionType.ROLE,
                argument.getName(),
                argument.getDescription(),
                argument.getRequired()
            )
        }

        ArgumentType.ATTACHMENT -> {
            slashCommandData.addOption(
                OptionType.STRING,
                argument.getName(),
                argument.getDescription(),
                argument.getRequired()
            )
        }

        ArgumentType.SUBCOMMAND -> {
            val subCommandOptionData = OptionData(
                OptionType.STRING,
                argument.getName(),
                argument.getDescription(),
                argument.getRequired(),
                false
            )
            argument.getChoices()?.forEach { choice ->
                subCommandOptionData.addChoice(choice, choice)
            }
            slashCommandData.addOptions(subCommandOptionData)
        }

        else -> {
            // Do nothing, this only happens with COMMAND, which is handled first before this loop
        }
    }
}

fun requireGuild(event: MessageReceivedEvent) {
    if (event.isFromGuild) {
        return
    }
    throw CommandExitException("This command must be run in a guild")
}

fun requireGuild(event: SlashCommandInteractionEvent) {
    if (event.guild != null) {
        return
    }
    throw CommandExitException("This command must be run in a guild")
}

fun requireUserPermission(event: MessageReceivedEvent, permission: Permission) {
    if (event.member?.hasPermission(permission) == true) {
        return
    }
    throw CommandExitException("You do not have permission to run this command")
}

fun requireUserPermission(event: SlashCommandInteractionEvent, permission: Permission) {
    if (event.member?.hasPermission(permission) == true) {
        return
    }
    throw CommandExitException("You do not have permission to run this command")
}

fun requireBotPermission(event: MessageReceivedEvent, permission: Permission) {
    requireGuild(event)
    if (event.guild.selfMember.hasPermission(permission) == true) {
        return
    }
    throw CommandExitException("I do not have permission to run this command")
}

fun requireBotPermission(event: SlashCommandInteractionEvent, permission: Permission) {
    requireGuild(event)
    if (event.guild?.selfMember?.hasPermission(permission) == true) {
        return
    }
    throw CommandExitException("I do not have permission to run this command")
}

fun checkIsNotGuildOwner(event: MessageReceivedEvent, userId: Long) {
    requireGuild(event)
    if (event.guild.owner?.idLong == userId) {
        throw CommandExitException("You cannot run this command on the guild owner")
    }
}

fun checkIsNotGuildOwner(event: SlashCommandInteractionEvent, userId: Long) {
    requireGuild(event)
    if (event.guild?.owner?.idLong == userId) {
        throw CommandExitException("You cannot run this command on the guild owner")
    }
}

suspend fun checkHierarchy(event: MessageReceivedEvent, userId: Long) {
    requireGuild(event)
    if (event.guild.retrieveMemberById(userId).await()?.let { event.guild.selfMember.canInteract(it) } == true) {
        return
    }
    throw CommandExitException("You cannot run this command on a user with a higher role than me")
}
suspend fun checkHierarchy(event: SlashCommandInteractionEvent, userId: Long) {
    requireGuild(event)
    if (event.guild?.retrieveMemberById(userId)?.await()?.let { event.guild!!.selfMember.canInteract(it) } == true) {
        return
    }
    throw CommandExitException("You cannot run this command on a user with a higher role than me")
}
suspend fun checkUserHierarchy(event: SlashCommandInteractionEvent, userId: Long) {
    requireGuild(event)
    val runningMember = event.member
    val targetMember = event.guild?.retrieveMemberById(userId)?.await()
    if (targetMember?.let { runningMember?.canInteract(it) } == true) {
        return
    }
    throw CommandExitException("You cannot run this command on a user with a higher role than you")
}
suspend fun checkUserHierarchy(event: MessageReceivedEvent, userId: Long) {
    requireGuild(event)
    val runningMember = event.member
    val targetMember: Member? = event.guild.retrieveMemberById(userId).await()
    if (targetMember?.let { runningMember?.canInteract(it) } == true) {
        return
    }
    throw CommandExitException("You cannot run this command on a user with a higher role than you")
}
