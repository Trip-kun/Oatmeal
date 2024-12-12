package tech.trip_kun.sinon.command

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.trip_kun.sinon.data.DatabaseException
import tech.trip_kun.sinon.data.entity.User
import tech.trip_kun.sinon.data.getUserDao
import tech.trip_kun.sinon.data.runSQLUntilMaxTries
import tech.trip_kun.sinon.exception.CommandExitException

class SetCurrencyPreference(private val jda: JDA) : Command() {
    init {
        val name = "setcurrencypreference"
        val description = "Set whether you want to receive notifications for currency events such as random events"
        addArgument(Argument(name, description, true, ArgumentType.COMMAND, null))
        addArgument(Argument("enabled", description, true, ArgumentType.BOOLEAN, null))
        initialize(jda)
    }

    override fun getCategory(): CommandCategory {
        return CommandCategory.SETTINGS
    }

    override suspend fun handler(event: MessageReceivedEvent) {
        val arguments = parseArguments(event)
        val enabled = arguments[0].getBooleanValue() ?: throw CommandExitException("Invalid arguments")
        try {
            var userObject: User? = null
            runSQLUntilMaxTries { userObject = getUserDao().queryForId(event.author.idLong) }
            if (userObject == null) {
                userObject = User(event.author.idLong)
            }
            userObject!!.allowCurrencyNotifications = enabled
            runSQLUntilMaxTries {
                val userDao = getUserDao()
                userDao.createOrUpdate(userObject)
            }
        } catch (e: DatabaseException) {
            throw CommandExitException("Sorry, I couldn't save your preference. Please try again later. (my developers have been notified)")
        }
        event.channel.sendMessage("Currency notifications for you have been ${if (enabled) "enabled" else "disabled"}").await()
    }

    override suspend fun handler(event: SlashCommandInteractionEvent) {
        val arguments = parseArguments(event)
        val enabled = arguments[0].getBooleanValue() ?: throw CommandExitException("Invalid arguments")
        try {
            var userObject: User? = null
            runSQLUntilMaxTries { userObject = getUserDao().queryForId(event.user.idLong) }
            if (userObject == null) {
                userObject = User(event.user.idLong)
            }
            userObject!!.allowCurrencyNotifications = enabled
            runSQLUntilMaxTries { getUserDao().createOrUpdate(userObject) }
        } catch (e: DatabaseException) {
            throw CommandExitException("Sorry, I couldn't save your preference. Please try again later. (my developers have been notified)")
        }
        event.hook.sendMessage("Currency notifications for you have been ${if (enabled) "enabled" else "disabled"}").await()
    }

}