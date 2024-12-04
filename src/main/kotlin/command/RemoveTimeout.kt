package tech.trip_kun.sinon.command

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.trip_kun.sinon.exception.CommandExitException
class RemoveTimeout(private val jda: JDA) : Command() {
    init {
        val name = "removetimeout"
        val description = "Removes the timeout for the user"
        addArgument(Argument(name, description, true, ArgumentType.COMMAND, null))
        addArgument(Argument("user", "User to remove timeout", true, ArgumentType.USER, null))
        initialize(jda)
    }
    override fun getCategory(): CommandCategory {
        return CommandCategory.ESSENTIAL
    }

    override fun handler(event: MessageReceivedEvent) {
        requireGuild(event)
        requireBotPermission(event, net.dv8tion.jda.api.Permission.MODERATE_MEMBERS)
        requireUserPermission(event, net.dv8tion.jda.api.Permission.MODERATE_MEMBERS)
        val arguments = parseArguments(event)
        val userId = arguments[0].getLongValue() ?: throw CommandExitException("Invalid arguments")
        val user = jda.retrieveUserById(userId).complete() ?: throw CommandExitException("Invalid arguments")
        val guild = event.guild
        val member = guild.retrieveMember(user).complete() ?: throw CommandExitException("Invalid arguments")
        val author = event.member ?: throw CommandExitException("Invalid arguments")
        checkUserHierarchy(event, member.idLong)
        checkHierarchy(event, member.idLong)
        val embedBuilder = commonWork(author, member)
        event.channel.sendMessageEmbeds(embedBuilder.build()).queue()
    }
    override fun handler(event: SlashCommandInteractionEvent) {
        requireGuild(event)
        requireBotPermission(event, net.dv8tion.jda.api.Permission.MODERATE_MEMBERS)
        requireUserPermission(event, net.dv8tion.jda.api.Permission.MODERATE_MEMBERS)
        val arguments = parseArguments(event)
        val userId = arguments[0].getLongValue() ?: throw CommandExitException("Invalid arguments")
        val user = jda.retrieveUserById(userId).complete() ?: throw CommandExitException("Invalid arguments")
        val guild = event.guild
        val member = guild?.retrieveMember(user)?.complete() ?: throw CommandExitException("Invalid arguments")
        val author = event.member ?: throw CommandExitException("Invalid arguments")
        checkUserHierarchy(event, member.idLong)
        checkHierarchy(event, member.idLong)
        val embedBuilder = commonWork(author, member)
        event.channel.sendMessageEmbeds(embedBuilder.build()).queue()
    }
    private fun commonWork(author: Member, member: Member): EmbedBuilder {
        val embedBuilder = EmbedBuilder()
        if (member.isTimedOut()) {
            member.removeTimeout()
            embedBuilder.setTitle("Removed timeout for ${member.effectiveName}")
        } else {
            embedBuilder.setTitle("${member.effectiveName} is not timed out")
        }
        return embedBuilder
    }
}