package tech.trip_kun.sinon.command

import com.j256.ormlite.dao.Dao
import dev.minn.jda.ktx.coroutines.await
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.jsoup.Jsoup
import tech.trip_kun.sinon.Logger
import tech.trip_kun.sinon.data.entity.Guild
import tech.trip_kun.sinon.data.entity.ScoopEntry
import tech.trip_kun.sinon.data.getGuildDao
import tech.trip_kun.sinon.data.getScoopEntryDao
import tech.trip_kun.sinon.data.runSQLUntilMaxTries
import tech.trip_kun.sinon.exception.CommandExitException
import tech.trip_kun.sinon.getDispatcher
private val ktorClient = HttpClient(CIO)
private val scoopCoroutineScope = CoroutineScope(getDispatcher())
class SetScoopChannel(private val jda: JDA) : Command() {
    init {
        val name = "setscoopchannel"
        val description = "Set the Student Scoop channel (not including a channel will disable Student Scoop posting)."
        addArgument(Argument(name, description, true, ArgumentType.COMMAND, null))
        addArgument(
            Argument(
                "channel",
                "The channel to set as the scoop channel.",
                false,
                ArgumentType.CHANNEL,
                null
            )
        )
        initialize(jda)
        scoopCoroutineScope.launch {
            while (true) {
                try {
                    handleScoops()
                } catch (e: Exception) {
                    Logger.error("Reminder handling failed: ${e.message}")
                }
                kotlinx.coroutines.delay(1000 * 60 * 60) // Runs once an hour
            }
        }
    }
    private suspend fun handleScoops() {
        var scoopEntryDao: Dao<ScoopEntry, Long>? = null
        var guildDao: Dao<Guild, Long>? = null
        runSQLUntilMaxTries { scoopEntryDao = getScoopEntryDao() }
        runSQLUntilMaxTries { guildDao = getGuildDao() }
        var guilds: List<Guild>? = null
        runSQLUntilMaxTries { guilds = guildDao?.queryBuilder()?.where()?.ne("scoopChannelId", 0)?.query()}
        val guildsJDA: ArrayList<net.dv8tion.jda.api.entities.Guild> = ArrayList()
        for (guild in jda.guilds) {
            val guildJDA = jda.getGuildById(guild.id)
            if (guildJDA != null) {
                guildsJDA.add(guildJDA)
            }
        }
        val listDoc = Jsoup.parse(ktorClient.get("https://blogs.mtu.edu/stu-org-news/category/todays-issue/").bodyAsText())
        val docLink = listDoc.select("h2.entry-title").first()?.getElementsByTag("a")?.first()?.attr("abs:href")
        if (docLink == null) {
            Logger.error("Something went wrong finding the student scoop link")
            return
        }
        val doc = Jsoup.parse(ktorClient.get(docLink).bodyAsText())
        val importantPics = doc.select("a[href].fancybox")
        importantPics.forEach {
            val link = it.attr("abs:href")
            var scoopEntries: List<ScoopEntry>? = null
            runSQLUntilMaxTries { scoopEntries = scoopEntryDao?.queryBuilder()?.where()?.eq("messageLink", link)?.query() }
            guilds?.forEach {it2 ->
                if (scoopEntries?.none { e -> e.guild.id == it2.id } == true) {
                    val embedBuilder = EmbedBuilder()
                    embedBuilder.setTitle("Student Scoop")
                    embedBuilder.setImage(link)
                    val channel = guildsJDA.find { g->g.idLong == it2.id }?.getTextChannelById(it2.scoopChannelId)
                    val message = channel?.sendMessageEmbeds(embedBuilder.build())?.await()
                    if (message!=null) {
                        val newEntry = ScoopEntry(it2, message.idLong, channel.idLong, link)
                        runSQLUntilMaxTries { scoopEntryDao?.create(newEntry) }
                    }
                }
            }
        }


    }
    override fun getCategory(): CommandCategory {
        return CommandCategory.UTILITY
    }

    override suspend fun handler(event: MessageReceivedEvent) {
        requireGuild(event)
        requireUserPermission(event, Permission.MANAGE_SERVER)
        val arguments = parseArguments(event)
        if (arguments.isEmpty()) {
            event.channel.sendMessageEmbeds(commonWork(event.guild, null).build()).await()
        } else {
            val channel = arguments[0].getLongValue()?.let { event.guild.getTextChannelById(it) }
            event.channel.sendMessageEmbeds(commonWork(event.guild, channel).build()).await()
        }
    }

    override suspend fun handler(event: SlashCommandInteractionEvent) {
        requireGuild(event)
        requireUserPermission(event, Permission.MANAGE_SERVER)
        val arguments = parseArguments(event)
        if (arguments.isEmpty()) {
            event.channel.sendMessageEmbeds(commonWork(event.guild, null).build()).await()
        } else {
            val channel = arguments[0].getLongValue()?.let { event.guild?.getTextChannelById(it) }
            event.channel.sendMessageEmbeds(commonWork(event.guild, channel).build()).await()
        }
    }
    private suspend fun commonWork(guildJDA: net.dv8tion.jda.api.entities.Guild?, channel: TextChannel?): EmbedBuilder {
        var guildDao: Dao<Guild, Long>? = null
        runSQLUntilMaxTries {
            guildDao = getGuildDao()
        }
        val embed = EmbedBuilder()
        embed.setTitle("Student Scoop Channel")
        runSQLUntilMaxTries {
            if (guildJDA ==null) {
                throw CommandExitException("Guild not found") // This won't happen because of the requireGuild check but makes kotlin shut up
            }
            var guild = guildDao?.queryForId(guildJDA.idLong)
            if (guild == null) {
                guild = Guild(guildJDA.idLong)
            }
            guild.scoopChannelId = channel?.idLong ?: 0
            guildDao?.createOrUpdate(guild)
        }
        if (channel == null) {
            embed.setDescription("Scoop channel disabled")
        } else {
            embed.setDescription("Scoop channel set to ${channel.asMention}")
        }
        return embed
    }
}