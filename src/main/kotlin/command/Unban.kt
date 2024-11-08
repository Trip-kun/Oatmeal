package tech.trip_kun.sinon.command

import com.j256.ormlite.dao.Dao
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.trip_kun.sinon.data.entity.BanEntry
import tech.trip_kun.sinon.data.getBanEntryDao
import tech.trip_kun.sinon.data.runSQLUntilMaxTries
import tech.trip_kun.sinon.exception.CommandExitException

class Unban(private val jda: JDA): Command() {
    init {
        val name = "unban"
        val description = "Unbans a user from the server"
        addArgument(Argument(name, description, true, ArgumentType.COMMAND, null))
        addArgument(Argument("userid", "The id of the user to unban", true, ArgumentType.WORD, null))
        initialize(jda)
    }
    override fun getCategory(): CommandCategory {
        return CommandCategory.MODERATION
    }

    override fun handler(event: MessageReceivedEvent) {
        requireGuild(event)
        requireBotPermission(event, Permission.BAN_MEMBERS)
        requireUserPermission(event, Permission.BAN_MEMBERS)
        val arguments = parseArguments(event)
        val userIdString = arguments[0].getStringValue() ?: throw CommandExitException("Invalid arguments")
        val userId = userIdString.toLongOrNull() ?: throw CommandExitException("Invalid arguments")
        val user = jda.retrieveUserById(userId).complete() ?: throw CommandExitException("Invalid arguments")
        val guild = event.guild
        guild.unban(user).queue()
        event.channel.sendMessage("Unbanned ${user.asTag}").queue()
        runSQLUntilMaxTries {
            val banEntryDao: Dao<BanEntry, Int> = getBanEntryDao()
            val banEntries = banEntryDao.queryBuilder()
                .where().eq("userId", userId)
                .and()
                .eq("guildId", guild.idLong).query()
            for (banEntry in banEntries) {
                banEntryDao.delete(banEntry)
            }
        }
    }

    override fun handler(event: SlashCommandInteractionEvent) {
        requireGuild(event)
        requireBotPermission(event, Permission.BAN_MEMBERS)
        requireUserPermission(event, Permission.BAN_MEMBERS)
        val arguments = parseArguments(event)
        val userId = arguments[0].getLongValue() ?: throw CommandExitException("Invalid arguments")
        val user = jda.retrieveUserById(userId).complete() ?: throw CommandExitException("Invalid arguments")
        val guild = event.guild
        guild?.unban(user)?.queue()
        event.hook.sendMessage("Unbanned ${user.asTag}").queue()
        runSQLUntilMaxTries {
            val banEntryDao: Dao<BanEntry, Int> = getBanEntryDao()
            val query = banEntryDao.queryBuilder().where()
                .eq("userId", userId)
                .and()
                .eq("guildId", guild?.idLong ?: 0)
                .prepare()
            val banEntries = banEntryDao.query(query)
            for (banEntry in banEntries) {
                banEntryDao.delete(banEntry)
            }
        }
    }
}