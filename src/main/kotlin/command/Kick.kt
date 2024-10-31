package tech.trip_kun.sinon.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.trip_kun.sinon.exception.CommandExitException

class Kick(private var jda: JDA) : Command() {
    init {
        val name = "kick"
        val description = "Kick a user"
        addArgument(Argument(name, description, true, ArgumentType.COMMAND, null))
        addArgument(Argument("user", "User to kick", true, ArgumentType.USER, null))
        initialize(jda)
    }

    override fun getCategory(): CommandCategory {
        return CommandCategory.MODERATION
    }

    override fun handler(event: MessageReceivedEvent) {
        requireGuild(event)
        requireBotPermission(event, Permission.KICK_MEMBERS)
        requireUserPermission(event, Permission.KICK_MEMBERS)
        val arguments = parseArguments(event)
        val userId = arguments[0].getLongValue() ?: throw CommandExitException("Invalid arguments")
        val user = jda.retrieveUserById(userId).complete() ?: throw CommandExitException("Invalid arguments")
        val guild = event.guild
        val member = guild?.retrieveMember(user)?.complete() ?: throw CommandExitException("Invalid arguments")
        checkIsNotGuildOwner(event, member.idLong)
        member.kick().queue()
        event.channel.sendMessage("Kicked ${member.effectiveName}").queue()
    }

    override fun handler(event: SlashCommandInteractionEvent) {
        requireGuild(event)
        requireBotPermission(event, Permission.KICK_MEMBERS)
        requireUserPermission(event, Permission.KICK_MEMBERS)
        val arguments = parseArguments(event)
        val userId = arguments[0].getLongValue() ?: throw CommandExitException("Invalid arguments")
        val user = jda.retrieveUserById(userId).complete() ?: throw CommandExitException("Invalid arguments")
        val guild = event.guild
        val member = guild?.retrieveMember(user)?.complete() ?: throw CommandExitException("Invalid arguments")
        checkIsNotGuildOwner(event, member.idLong)
        member.kick().queue()
        event.hook.sendMessage("Kicked ${member.effectiveName}").queue()
    }
}