package tech.trip_kun.sinon.command

import com.j256.ormlite.dao.Dao
import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.trip_kun.sinon.data.entity.Guild
import tech.trip_kun.sinon.data.getGuildDao
import tech.trip_kun.sinon.data.runSQLUntilMaxTries
import tech.trip_kun.sinon.exception.CommandExitException

class SetStarboardLimit(private val jda: JDA): Command() {
    init {
        val name = "setstarboardlimit"
        val description = "Set the starboard limit"
        addArgument(Argument(name, description, true, ArgumentType.COMMAND, null))
        addArgument(Argument("limit", "The limit of stars required to be on the starboard", true, ArgumentType.UINT, null))
        initialize(jda)
    }
    override fun getCategory(): CommandCategory {
        return CommandCategory.UTILITY
    }

    override suspend fun handler(event: MessageReceivedEvent) {
        requireGuild(event)
        requireUserPermission(event, Permission.MANAGE_SERVER)
        val arguments = parseArguments(event)
        val limit = arguments[0].getIntValue() ?: throw CommandExitException("Invalid arguments") // Exception should not be called but just in case
        event.channel.sendMessageEmbeds(commonWork(event.guild, limit).build()).await()
    }

    override suspend fun handler(event: SlashCommandInteractionEvent) {
        requireGuild(event)
        requireUserPermission(event, Permission.MANAGE_SERVER)
        val arguments = parseArguments(event)
        val limit = arguments[0].getIntValue() ?: throw CommandExitException("Invalid arguments") // Exception should not be called but just in case
        event.hook.sendMessageEmbeds(commonWork(event.guild, limit).build()).await()
    }
    private suspend fun commonWork(guildJDA: net.dv8tion.jda.api.entities.Guild?, limit: Int): EmbedBuilder {
        var guildDao: Dao<Guild, Long>? = null
        runSQLUntilMaxTries {
            guildDao = getGuildDao()
        }
        guildJDA?.let { // Because of the requireGuild check, this will never be null
            runSQLUntilMaxTries {
                var guild = guildDao?.queryForId(guildJDA.idLong)
                if (guild == null) {
                    guild = Guild(guildJDA.idLong)
                }
                guild.starboardLimit = limit
                guildDao?.createOrUpdate(guild)
            }
        }
        val embed = EmbedBuilder()
        embed.setTitle("Starboard Limit")
        embed.setDescription("Starboard limit set to $limit")
        return embed
    }
}