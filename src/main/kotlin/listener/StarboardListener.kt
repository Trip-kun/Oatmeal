package tech.trip_kun.sinon.listener

import kotlinx.coroutines.CoroutineScope
import tech.trip_kun.sinon.getDispatcher

import com.j256.ormlite.dao.Dao
import dev.minn.jda.ktx.coroutines.await
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
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

private val starboardListenerCoroutineScope = CoroutineScope(getDispatcher())

@ListenerClass
@ListenerIntents(GUILD_MESSAGE_REACTIONS)
@ListenerIntents(MESSAGE_CONTENT)
class StarboardListener @ListenerConstructor constructor(private val jda: JDA) : ListenerAdapter() {
    init {
        Logger.info("StarboardListener initialized")
    }

    override fun onMessageReactionRemoveEmoji(event: MessageReactionRemoveEmojiEvent) {
        if (!event.isFromGuild) return
        val guildChannel = event.guildChannel
        starboardListenerCoroutineScope.launch {
            val message = guildChannel.retrieveMessageById(event.messageId).await()
            commonWork(event.guild, message)
        }
    }

    override fun onMessageReactionRemoveAll(event: MessageReactionRemoveAllEvent) {
        if (!event.isFromGuild) return
        val guildChannel = event.guildChannel
        starboardListenerCoroutineScope.launch {
            val message = guildChannel.retrieveMessageById(event.messageId).await()
            commonWork(event.guild, message)
        }
    }

    override fun onGenericMessageReaction(event: GenericMessageReactionEvent) {
        if (!event.isFromGuild) return
        val guildChannel = event.guildChannel
        starboardListenerCoroutineScope.launch {
            val message = guildChannel.retrieveMessageById(event.messageId).await()
            commonWork(event.guild, message)
        }
    }


}

private fun generateStarboardEmbeds(member: Member, count: Int, message: String): EmbedBuilder {
    val embed = EmbedBuilder()
    embed.setAuthor(member.user.asMention, null, member.user.effectiveAvatarUrl)
    embed.setDescription(message)
    embed.setFooter("⭐ $count")
    return embed
}

private suspend fun commonWork(guildJDA: net.dv8tion.jda.api.entities.Guild, message: Message) {
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
        .filter { it.emoji.type == Emoji.Type.UNICODE && it.emoji.asUnicode() == Emoji.fromUnicode("⭐") }
        .forEach {
            count = it.count
        }
    if (count >= guild!!.starboardLimit) {
        var starboardMessage: Message? = null
        if (starboardEntry!!.starboardMessageId != 0L) {
            starboardMessage = try {
                channel.retrieveMessageById(starboardEntry!!.starboardMessageId).await()
            } catch (e: ErrorResponseException) {
                Logger.error("Failed to retrieve starboard message", e)
                null
            }
        }
        if (starboardMessage == null) {
            val embed = generateStarboardEmbeds(message.member!!, count, message.contentRaw)
            starboardMessage = channel.sendMessageEmbeds(embed.build()).await()
            starboardEntry!!.starboardMessageId = starboardMessage.idLong
        } else {
            val embed = generateStarboardEmbeds(message.member!!, count, message.contentRaw)
            try {
                starboardMessage.editMessageEmbeds(embed.build()).await()
            } catch (e: Exception) {
                Logger.error("Failed to edit starboard message", e)
            }
        }
        runSQLUntilMaxTries {
            starboardEntryDao?.createOrUpdate(starboardEntry)
        }
    } else {
        if (starboardEntry!!.starboardMessageId != 0L) {
            try {
                channel.deleteMessageById(starboardEntry!!.starboardMessageId).await()
            } catch (e: Exception) {
                Logger.error("Failed to delete starboard message", e)
            }
            starboardEntry!!.starboardMessageId = 0
            runSQLUntilMaxTries {
                starboardEntryDao?.createOrUpdate(starboardEntry)
            }
        }
    }
}