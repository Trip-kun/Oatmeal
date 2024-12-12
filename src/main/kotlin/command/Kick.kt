package tech.trip_kun.sinon.command

import dev.minn.jda.ktx.coroutines.await
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

    override suspend fun handler(event: MessageReceivedEvent) {
        requireGuild(event)
        requireBotPermission(event, Permission.KICK_MEMBERS)
        requireUserPermission(event, Permission.KICK_MEMBERS)
        val arguments = parseArguments(event)
        val userId = arguments[0].getLongValue() ?: throw CommandExitException("Invalid arguments")
        val user = jda.retrieveUserById(userId).await() ?: throw CommandExitException("Invalid arguments")
        checkHierarchy(event, user.idLong)
        checkUserHierarchy(event, user.idLong)
        if (userId == event.author.idLong) {
            throw CommandExitException("You cannot kick yourself")
        }
        if (userId == event.jda.selfUser.idLong) {
            throw CommandExitException("You cannot kick me using this command")
        }
        val guild = event.guild
        val member = guild.retrieveMember(user).await() ?: throw CommandExitException("Invalid arguments")
        checkIsNotGuildOwner(event, member.idLong)
        member.kick().queue()
        event.channel.sendMessage("Kicked ${member.effectiveName}").queue()
    }

    override suspend fun handler(event: SlashCommandInteractionEvent) {
        requireGuild(event)
        requireBotPermission(event, Permission.KICK_MEMBERS)
        requireUserPermission(event, Permission.KICK_MEMBERS)
        val arguments = parseArguments(event)
        val userId = arguments[0].getLongValue() ?: throw CommandExitException("Invalid arguments")
        val user = jda.retrieveUserById(userId).await() ?: throw CommandExitException("Invalid arguments")
        checkHierarchy(event, user.idLong)
        checkUserHierarchy(event, user.idLong)
        if (userId == event.user.idLong) {
            throw CommandExitException("You cannot kick yourself")
        }
        if (userId == event.jda.selfUser.idLong) {
            throw CommandExitException("You cannot kick me using this command")
        }
        val guild = event.guild
        val member = guild?.retrieveMember(user)?.await() ?: throw CommandExitException("Invalid arguments")
        checkIsNotGuildOwner(event, member.idLong)
        member.kick().queue()
        event.hook.sendMessage("Kicked ${member.effectiveName}").queue()
    }
}