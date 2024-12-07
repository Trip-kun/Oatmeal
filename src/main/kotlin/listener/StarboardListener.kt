package tech.trip_kun.sinon.listener

import com.j256.ormlite.dao.Dao
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.react.*
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.requests.GatewayIntent.*
import org.apache.commons.logging.Log
import tech.trip_kun.sinon.Logger
import tech.trip_kun.sinon.annotations.ListenerClass
import tech.trip_kun.sinon.annotations.ListenerConstructor
import tech.trip_kun.sinon.annotations.ListenerIntents
import tech.trip_kun.sinon.data.entity.Guild
import tech.trip_kun.sinon.data.entity.StarboardEntry
import tech.trip_kun.sinon.data.getGuildDao
import tech.trip_kun.sinon.data.getStarboardEntryDao
import tech.trip_kun.sinon.data.runSQLUntilMaxTries
@ListenerClass
@ListenerIntents(GatewayIntent.GUILD_MESSAGE_REACTIONS)
@ListenerIntents(MESSAGE_CONTENT)
class StarboardListener @ListenerConstructor constructor(private val jda: JDA): ListenerAdapter() {
    init {
        Logger.info("StarboardListener initialized")
    }
    override fun onMessageReactionRemoveEmoji(event: MessageReactionRemoveEmojiEvent) {
        if (!event.isFromGuild) return
        val guildChannel  = event.guildChannel
        val message = guildChannel.retrieveMessageById(event.messageId).complete()
        commonWork(event.guild, message)
    }

    override fun onMessageReactionRemoveAll(event: MessageReactionRemoveAllEvent) {
        if (!event.isFromGuild) return
        val guildChannel  = event.guildChannel
        val message = guildChannel.retrieveMessageById(event.messageId).complete()
        commonWork(event.guild, message)
    }

    override fun onGenericMessageReaction(event: GenericMessageReactionEvent) {
        if (!event.isFromGuild) return
        val guildChannel  = event.guildChannel
        val message = guildChannel.retrieveMessageById(event.messageId).complete()
        commonWork(event.guild, message)
    }



}

private fun generateStarboardEmbeds(member: Member, count: Int, message: String): EmbedBuilder {
    val embed = EmbedBuilder()
    embed.setAuthor(member.user.asTag, null, member.user.effectiveAvatarUrl)
    embed.setDescription(message)
    embed.setFooter("⭐ $count")
    return embed
}

private fun commonWork(guildJDA: net.dv8tion.jda.api.entities.Guild, message: Message) {
    var guildDao: Dao<Guild, Long>? = null
    var starboardEntryDao: Dao<StarboardEntry, Long>? = null
    runSQLUntilMaxTries {
        guildDao = getGuildDao()
        starboardEntryDao = getStarboardEntryDao()
    }
    var guild: Guild? = null
    runSQLUntilMaxTries {
        guild = guildDao?.queryForId(guildJDA.idLong)
        if (guild == null) {
            guild = Guild(guildJDA.idLong)
            guildDao?.createOrUpdate(guild)
        }
    }

    if (guild == null) return // This should never happen, but if it does, it indicates the database is not working
    if ((guild?.starboardChannelId ?: 0) == 0L) return // Starboard is not enabled
    val channel = guildJDA.getTextChannelById(guild!!.starboardChannelId) ?: return // Starboard channel not found
    var starboardEntry: StarboardEntry? = null
    runSQLUntilMaxTries {
        starboardEntry = starboardEntryDao?.queryForId(message.idLong)
    }
    if (starboardEntry == null) {
        starboardEntry = StarboardEntry(guild!!, message.idLong, channel.idLong)
    }
    var count = 0
    message.reactions
        .filter {it.emoji.type== Emoji.Type.UNICODE && it.emoji.asUnicode() == Emoji.fromUnicode("⭐") }
        .forEach {
            count = it.count
        }
    if (count >= guild!!.starboardLimit) {
        if (starboardEntry!!.starboardMessageId == 0L) {
            var starboardMessage: Message? = null
            starboardMessage = try {
                channel.retrieveMessageById(starboardEntry!!.starboardMessageId).complete()
            } catch (e: ErrorResponseException) {
                null
            }
            if (starboardMessage == null) {
                val embed = generateStarboardEmbeds(message.member!!, count, message.contentRaw)
                starboardMessage = channel.sendMessageEmbeds(embed.build()).complete()
                starboardEntry!!.starboardMessageId = starboardMessage.idLong
            } else {
                val embed = generateStarboardEmbeds(message.member!!, count, message.contentRaw)
                starboardMessage.editMessageEmbeds(embed.build()).queue()
            }
        }
        runSQLUntilMaxTries {
            starboardEntryDao?.createOrUpdate(starboardEntry)
        }
    } else {
        if (starboardEntry!!.starboardMessageId != 0L) {
            channel.deleteMessageById(starboardEntry!!.starboardMessageId).queue()
            starboardEntry!!.starboardMessageId = 0
            runSQLUntilMaxTries {
                starboardEntryDao?.createOrUpdate(starboardEntry)
            }
        }
    }
}