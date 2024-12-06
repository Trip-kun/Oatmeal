package tech.trip_kun.sinon.command

import com.j256.ormlite.dao.Dao
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.trip_kun.sinon.data.entity.BanEntry
import tech.trip_kun.sinon.data.getBanEntryDao
import tech.trip_kun.sinon.data.runSQLUntilMaxTries
import tech.trip_kun.sinon.exception.CommandExitException
import java.time.format.DateTimeParseException
import java.util.*
import java.util.concurrent.TimeUnit

class Ban(private var jda: JDA) : Command() {
    private var timerTask: TimerTask
    private val timer = Timer()

    init {
        val name = "ban"
        val description = "Bans a user from the server"
        addArgument(Argument(name, description, true, ArgumentType.COMMAND, null))
        addArgument(Argument("usertoban", "The user to ban", true, ArgumentType.USER, null))
        addArgument(Argument("clear", "The time in hours to clear old messages", false, ArgumentType.UINT, null))
        addArgument(
            Argument(
                "expiry",
                "The time in seconds until the ban expires. Excluding does a permanent ban.",
                false,
                ArgumentType.TEXT,
                null
            )
        )
        addArgument(Argument("reason", "The reason for the ban", false, ArgumentType.TEXT, null))
        initialize(jda)
        timerTask = object : TimerTask() {
            override fun run() {
                handleBans()
            }
        }
        timer.schedule(timerTask, 0, 60 * 1000)
    }

    private fun handleBans() {
        var banEntryDao: Dao<BanEntry, Int>? = null
        runSQLUntilMaxTries { banEntryDao = getBanEntryDao() }
        var bans: List<BanEntry>? = null
        runSQLUntilMaxTries {
            if (banEntryDao != null) {
                val queryBuilder = banEntryDao?.queryBuilder()
                queryBuilder?.where()?.le("time", System.currentTimeMillis())
                bans = queryBuilder?.query()
            }
        }
        bans?.forEach {
            val banEntry = it
            val guild = jda.getGuildById(it.guildId)
            if (guild != null) {
                val user = jda.retrieveUserById(banEntry.userId).complete()
                if (user != null) {
                    guild.unban(user).queue()
                }
            }
            runSQLUntilMaxTries { banEntryDao?.delete(banEntry) }
        }
    }

    override fun getCategory(): CommandCategory {
        return CommandCategory.MODERATION
    }

    override fun handler(event: MessageReceivedEvent) {
        requireGuild(event)
        requireBotPermission(event, Permission.BAN_MEMBERS)
        requireUserPermission(event, Permission.BAN_MEMBERS)
        val arguments = parseArguments(event)
        val userToBeBannedId = arguments[0].getLongValue() ?: throw CommandExitException("Invalid arguments")
        val userToBeBanned =
            jda.retrieveUserById(userToBeBannedId).complete() ?: throw CommandExitException("User not found")
        checkHierarchy(event, userToBeBanned.idLong)
        checkUserHierarchy(event, userToBeBanned.idLong)
        if (userToBeBannedId == event.author.idLong) {
            throw CommandExitException("You cannot ban yourself")
        }
        if (userToBeBannedId == event.jda.selfUser.idLong) {
            throw CommandExitException("You cannot ban me using this command")
        }
        var clearTime = 0
        var expiry = ""
        var reason = ""
        if (arguments.size > 1) {
            clearTime = arguments[1].getIntValue() ?: 0
        }
        if (arguments.size > 2) {
            expiry = arguments[2].getStringValue() ?: ""
        }
        if (arguments.size > 3) {
            reason = arguments[3].getStringValue() ?: ""
        }
        commonWork(userToBeBanned, event.author, event.guild, reason, clearTime, expiry)
        event.channel.sendMessage("User has been banned").queue()


    }

    override fun handler(event: SlashCommandInteractionEvent) {
        requireGuild(event)
        requireBotPermission(event, Permission.BAN_MEMBERS)
        requireUserPermission(event, Permission.BAN_MEMBERS)
        val arguments = parseArguments(event)
        val userToBeBannedId = arguments[0].getLongValue() ?: throw CommandExitException("Invalid arguments")
        val userToBeBanned =
            jda.retrieveUserById(userToBeBannedId).complete() ?: throw CommandExitException("User not found")
        checkHierarchy(event, userToBeBanned.idLong)
        checkUserHierarchy(event, userToBeBanned.idLong)
        if (userToBeBannedId == event.user.idLong) {
            throw CommandExitException("You cannot ban yourself")
        }
        if (userToBeBannedId == event.jda.selfUser.idLong) {
            throw CommandExitException("You cannot ban me using this command")
        }
        var clearTime = 0
        var expiry = ""
        var reason = ""
        if (arguments.size > 1) {
            clearTime = arguments[1].getIntValue() ?: 0
        }
        if (arguments.size > 2) {
            expiry = arguments[2].getStringValue() ?: ""
        }
        if (arguments.size > 3) {
            reason = arguments[3].getStringValue() ?: ""
        }
        event.guild?.let { commonWork(userToBeBanned, event.user, it, reason, clearTime, expiry) }
        event.hook.sendMessage("User has been banned").queue()

    }

    private fun commonWork(
        userToBeBanned: User,
        userBanning: User,
        guild: Guild,
        reason: String?,
        clearTime: Int,
        expiry: String
    ) {
        if (clearTime > 168) {
            throw CommandExitException("Clear time cannot be more than 168 hours (7 days)")
        }
        var expiryUnix = 0L
        if (expiry.isNotEmpty()) {
            try {
                expiryUnix = parseTime(expiry, true, TimeZone.getDefault().toString())
            } catch (e: DateTimeParseException) {
                throw CommandExitException("Invalid expiry time")
            }
        }
        val banEntry = BanEntry().apply {
            userId = userToBeBanned.idLong
            guildId = guild.idLong
            this.reason = reason ?: "No reason provided"
            time = if (expiryUnix == 0L) {
                0
            } else {
                expiryUnix
            }
            bannedBy = userBanning.idLong
        }
        runSQLUntilMaxTries { getBanEntryDao().createOrUpdate(banEntry) }
        guild.ban(userToBeBanned, clearTime, TimeUnit.HOURS).queue()
    }
}
