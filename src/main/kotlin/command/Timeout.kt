package tech.trip_kun.sinon.command

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.trip_kun.sinon.exception.CommandExitException
import java.lang.IllegalArgumentException
import java.util.concurrent.TimeUnit

class Timeout(private val jda: JDA) : Command() {
    init {
        val name = "timeout"
        val description = "Timeout a user"
        addArgument(Argument(name, description, true, ArgumentType.COMMAND, null))
        addArgument(Argument("user", "User to timeout", true, ArgumentType.USER, null))
        addArgument(Argument("time", "Time to timeout the user (should be more than 0, and less than 28 days)", true, ArgumentType.INTEGER, null))
        addArgument(Argument("unit", "Unit of time ", true, ArgumentType.WORD, arrayListOf("seconds", "minutes", "hours", "days")))
        addArgument(Argument("reason", "Reason for the timeout", false, ArgumentType.TEXT, null))
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
        if (arguments.size < 3) {
            throw CommandExitException("Invalid arguments")
        }
        val userId = arguments[0].getLongValue() ?: throw CommandExitException("Invalid arguments")
        val member = event.guild.retrieveMemberById(userId).complete() ?: throw CommandExitException("Invalid arguments")
        val time = arguments[1].getIntValue() ?: throw CommandExitException("Invalid arguments")
        val unit = arguments[2].getStringValue() ?: throw CommandExitException("Invalid arguments")
        var reason = "No reason provided"
        if (arguments.size >=4) {
            reason = arguments[3].getStringValue() ?: reason
        }
        val author = event.member ?: throw CommandExitException("Invalid arguments")
        checkUserHierarchy(event, member.idLong)
        checkHierarchy(event, member.idLong)
        val embedBuilder = commonWork(author, member, time, unit, reason)
        event.channel.sendMessageEmbeds(embedBuilder.build()).queue()
    }

    override fun handler(event: SlashCommandInteractionEvent) {
        requireGuild(event)
        requireBotPermission(event, net.dv8tion.jda.api.Permission.MODERATE_MEMBERS)
        requireUserPermission(event, net.dv8tion.jda.api.Permission.MODERATE_MEMBERS)
        val arguments = parseArguments(event)
        if (arguments.size < 3) {
            throw CommandExitException("Invalid arguments")
        }
        val userId = arguments[0].getLongValue() ?: throw CommandExitException("Invalid arguments")
        val member = event.guild?.retrieveMemberById(userId)?.complete() ?: throw CommandExitException("Invalid arguments")
        val time = arguments[1].getIntValue() ?: throw CommandExitException("Invalid arguments")
        val unit = arguments[2].getStringValue() ?: throw CommandExitException("Invalid arguments")
        var reason = "No reason provided"
        if (arguments.size >=4) {
            reason = arguments[3].getStringValue() ?: reason
        }
        val author = event.member ?: throw CommandExitException("Invalid arguments")
        checkUserHierarchy(event, member.idLong)
        checkHierarchy(event, member.idLong)
        val embedBuilder = commonWork(author, member, time, unit, reason)
        event.channel.sendMessageEmbeds(embedBuilder.build()).queue()
    }
    private fun commonWork(author: Member, member: Member, time: Int, unit: String, reason: String): EmbedBuilder {
        if (member.idLong == author.idLong) {
            throw CommandExitException("You cannot timeout yourself")
        }
        if (member.idLong == author.jda.selfUser.idLong) {
            throw CommandExitException("You cannot timeout me using this command")
        }
        if (member.isOwner) {
            throw CommandExitException("You cannot timeout the owner")
        }
        try {
            when (unit) {
                "seconds" -> member.timeoutFor(time.toLong(), TimeUnit.SECONDS).queue()
                "minutes" -> member.timeoutFor(time.toLong(), TimeUnit.MINUTES).queue()
                "hours" -> member.timeoutFor(time.toLong(), TimeUnit.HOURS).queue()
                "days" -> member.timeoutFor(time.toLong(), TimeUnit.DAYS).queue()
                else -> throw CommandExitException("Invalid unit")
            }
        } catch (e: IllegalArgumentException) {
            throw CommandExitException("Invalid arguments: check your duration, it may be too long")
        }
        val embedBuilder = EmbedBuilder()
        embedBuilder.setTitle("Timeout")
        embedBuilder.setDescription("Timed out ${member.asMention} for $time $unit for $reason")
        embedBuilder.setFooter("Requested by ${author.asMention}", member.avatarUrl)
        return embedBuilder
    }
}