package tech.trip_kun.sinon.command

import com.j256.ormlite.dao.Dao
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.trip_kun.sinon.data.entity.Guild
import tech.trip_kun.sinon.data.getGuildDao
import tech.trip_kun.sinon.data.runSQLUntilMaxTries
import tech.trip_kun.sinon.exception.CommandExitException

class SetStarboardChannel(private val jda: JDA) : Command() {
    init {
        val name = "setstarboardchannel"
        val description = "Set the starboard channel (not including a channel will disable the starboard)"
        addArgument(Argument(name, description, true, ArgumentType.COMMAND, null))
        addArgument(
            Argument(
                "channel",
                "The channel to set as the starboard channel",
                false,
                ArgumentType.CHANNEL,
                null
            )
        )
        initialize(jda)
    }

    override fun getCategory(): CommandCategory {
        return CommandCategory.UTILITY
    }

    override fun handler(event: MessageReceivedEvent) {
        requireGuild(event)
        requireUserPermission(event, Permission.MANAGE_SERVER)
        val arguments = parseArguments(event)
        if (arguments.isEmpty()) {
            event.channel.sendMessageEmbeds(commonWork(event.guild, null).build()).queue()
        } else {
            val channel = arguments[0].getLongValue()?.let { event.guild.getTextChannelById(it) }
            event.channel.sendMessageEmbeds(commonWork(event.guild, channel).build()).queue()
        }
    }

    override fun handler(event: SlashCommandInteractionEvent) {
        requireGuild(event)
        requireUserPermission(event, Permission.MANAGE_SERVER)
        val arguments = parseArguments(event)
        if (arguments.isEmpty()) {
            event.hook.sendMessageEmbeds(commonWork(event.guild, null).build()).queue()
        } else {
            val channel = arguments[0].getLongValue()?.let { event.guild?.getTextChannelById(it) }
            event.hook.sendMessageEmbeds(commonWork(event.guild, channel).build()).queue()
        }
    }

    private fun commonWork(guildJDA: net.dv8tion.jda.api.entities.Guild?, channel: TextChannel?): EmbedBuilder {
        var guildDao: Dao<Guild, Long>? = null
        runSQLUntilMaxTries {
            guildDao = getGuildDao()
        }
        val embed = EmbedBuilder()
        embed.setTitle("Starboard Channel")
        runSQLUntilMaxTries {
            if (guildJDA == null) {
                throw CommandExitException("Guild not found") // This will never happen because of the requireGuild check
            }
            var guild = guildDao?.queryForId(guildJDA.idLong)
            if (guild == null) {
                guild = Guild(guildJDA.idLong)
            }
            guild.starboardChannelId = channel?.idLong ?: 0
            guildDao?.createOrUpdate(guild)
        }
        if (channel == null) {
            embed.setDescription("Starboard channel disabled")
        } else {
            embed.setDescription("Starboard channel set to ${channel.asMention}")
        }
        return embed
    }
}