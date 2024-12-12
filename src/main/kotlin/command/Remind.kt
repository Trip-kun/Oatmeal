package tech.trip_kun.sinon.command

import com.j256.ormlite.dao.Dao
import com.j256.ormlite.dao.GenericRawResults
import dev.minn.jda.ktx.coroutines.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.trip_kun.sinon.data.entity.Reminder
import tech.trip_kun.sinon.data.entity.User
import tech.trip_kun.sinon.data.getReminderDao
import tech.trip_kun.sinon.data.getUserDao
import tech.trip_kun.sinon.data.runSQLUntilMaxTries
import tech.trip_kun.sinon.exception.CommandExitException
import tech.trip_kun.sinon.getDispatcher
import java.text.DateFormat
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
private val remindCoroutineScope = CoroutineScope(getDispatcher())
class Remind(private val jda: JDA): Command() {
    private var timerTask: TimerTask
    private val timer = Timer()

    init {
        addArgument(Argument("remind", "Sets a reminder for the future", true, ArgumentType.COMMAND, null))
        addArgument(Argument("reminder", "The reminder message ", true, ArgumentType.TEXT, null))
        addArgument(
            Argument(
                "duration",
                "How far in the future to remind you of (remind me in X)",
                true,
                ArgumentType.TEXT,
                null
            )
        )
        addArgument(Argument("time", "The time to remind you at (remind me in X at Y)", false, ArgumentType.TEXT, null))
        initialize(jda)
        timerTask = object : TimerTask() {
            override fun run() {
                remindCoroutineScope.launch {
                    handleReminders()
                }
            }
        }
        timer.schedule(timerTask, 0, 30 * 1000)
    }
    private suspend fun handleReminders() {
        var reminderDao: Dao<Reminder, Long>? = null
        runSQLUntilMaxTries { reminderDao = getReminderDao() }
        val now = System.currentTimeMillis()
        var reminders: GenericRawResults<Reminder>? = null
        runSQLUntilMaxTries {
            if (reminderDao != null)
                reminders =
                    reminderDao?.queryRaw("SELECT * FROM reminders WHERE timeUnix <= $now", reminderDao!!.rawRowMapper)
        }
        reminders?.forEach {
            val user = it.user
            val channel = jda.retrieveUserById(user.id).await()?.openPrivateChannel()?.await()
            if (it.reminder.length>2000) {
                // Split the message into multiple messages
                val message = "I am here to remind you of: ${it.reminder}"
                var start = 0
                var end = 2000
                while (start < message.length) {
                    channel?.sendMessage(message.substring(start, end))?.queue()
                    start = end
                    end += 2000
                    if (end > message.length) {
                        end = message.length
                    }
                }
            } else {
                channel?.sendMessage("I am here to remind you of: ${it.reminder}")?.queue()
            }
            runSQLUntilMaxTries { reminderDao?.delete(it) }

        }
    }

    override fun getCategory(): CommandCategory {
        return CommandCategory.UTILITY
    }

    override suspend fun handler(event: MessageReceivedEvent) {
        val arguments = parseArguments(event)
        if (arguments.size < 2) {
            throw CommandExitException("Invalid arguments")
        }
        val reminder = arguments[0].getStringValue() ?: throw CommandExitException("Invalid arguments")
        val durationIn = arguments[1].getStringValue() ?: throw CommandExitException("Invalid arguments")
        // If duration contains anything smaller than a day, we don't accept a time and will command except if one is provided
        // We use both full and shortened forms of the time units
        if (arguments.size > 2) {
            if (durationIn.contains("s") || durationIn.contains("sec") || durationIn.contains("second") || durationIn.contains(
                    "seconds"
                ) ||
                durationIn.contains("m") || durationIn.contains("min") || durationIn.contains("minute") || durationIn.contains(
                    "minutes"
                ) ||
                durationIn.contains("h") || durationIn.contains("hr") || durationIn.contains("hour") || durationIn.contains(
                    "hours"
                )
            ) {
                throw CommandExitException("Invalid argument: time cannot be provided for durations less than a day")
            }
            val time = arguments[2].getStringValue() ?: throw CommandExitException("Invalid arguments")
            val heldUser = HeldUser()
            runSQLUntilMaxTries { heldUser.user = getUserDao().queryForId(event.author.idLong) }
            if (heldUser.user == null) {
                heldUser.user = User(event.author.idLong)
            }
            val message = commonWork(durationIn, time, reminder, heldUser.user!!)
            event.channel.sendMessage(message).queue()
            runSQLUntilMaxTries { getUserDao().createOrUpdate(heldUser.user) }
        } else {
            val heldUser = HeldUser()
            runSQLUntilMaxTries { heldUser.user = getUserDao().queryForId(event.author.idLong) }
            if (heldUser.user == null) {
                heldUser.user = User(event.author.idLong)
            }
            val message = commonWork(durationIn, null, reminder, heldUser.user!!)
            event.channel.sendMessage(message).queue()
            runSQLUntilMaxTries { getUserDao().createOrUpdate(heldUser.user) }
        }
    }

    override suspend fun handler(event: SlashCommandInteractionEvent) {
        val arguments = parseArguments(event)
        if (arguments.size < 2) {
            throw CommandExitException("Invalid arguments")
        }
        val reminder = arguments[0].getStringValue() ?: throw CommandExitException("Invalid arguments")
        val durationIn = arguments[1].getStringValue() ?: throw CommandExitException("Invalid arguments")
        // If duration contains anything smaller than a day, we don't accept a time and will command except if one is provided
        // We use both full and shortened forms of the time units
        if (arguments.size > 2) {
            if (durationIn.contains("s") || durationIn.contains("sec") || durationIn.contains("second") || durationIn.contains(
                    "seconds"
                ) ||
                durationIn.contains("m") || durationIn.contains("min") || durationIn.contains("minute") || durationIn.contains(
                    "minutes"
                ) ||
                durationIn.contains("h") || durationIn.contains("hr") || durationIn.contains("hour") || durationIn.contains(
                    "hours"
                )
            ) {
                throw CommandExitException("Invalid argument: time cannot be provided for durations less than a day")
            }
            val time = arguments[2].getStringValue() ?: throw CommandExitException("Invalid arguments")
            val heldUser = HeldUser()
            runSQLUntilMaxTries { heldUser.user = getUserDao().queryForId(event.user.idLong) }
            if (heldUser.user == null) {
                heldUser.user = User(event.user.idLong)
            }
            val message = commonWork(durationIn, time, reminder, heldUser.user!!)
            event.hook.sendMessage(message).queue()
            runSQLUntilMaxTries { getUserDao().createOrUpdate(heldUser.user) }
        } else {
            val heldUser = HeldUser()
            runSQLUntilMaxTries { heldUser.user = getUserDao().queryForId(event.user.idLong) }
            if (heldUser.user == null) {
                heldUser.user = User(event.user.idLong)
            }
            val message = commonWork(durationIn, null, reminder, heldUser.user!!)
            event.hook.sendMessage(message).queue()
            runSQLUntilMaxTries { getUserDao().createOrUpdate(heldUser.user) }
        }
    }

    private class HeldUser {
        var user: User? = null
    }
    private suspend fun commonWork(duration: String, time: String?, reminder: String, user: User):String {
        try {
            var unixTime: Long
            try {
                unixTime = parseTime(duration, time == null, user.timeZone)
                if (time != null) {
                    unixTime += parseClockTime(time)
                }
            } catch (e: DateTimeParseException) {
                throw CommandExitException("Invalid time format: ${e.message}")
            }
            val reminderObject = Reminder(user)
            reminderObject.reminder = reminder
            reminderObject.user = user
            reminderObject.timeUnix = unixTime
            runSQLUntilMaxTries { getReminderDao().createOrUpdate(reminderObject) }
            val timeZone = TimeZone.getTimeZone(user.timeZone)
            val dateFormat = DateFormat.getDateTimeInstance()
            dateFormat.timeZone = timeZone
            val date = Date(unixTime)
            return "Reminder set for ${dateFormat.format(date)} in ${User(0).timeZone}"
        } catch (e: NumberFormatException) {
            throw CommandExitException("Invalid time format: ${e.message}")
        } catch (e: DateTimeParseException) {
            throw CommandExitException("Invalid time format: ${e.message}")
        }
    }
}

@Throws(DateTimeParseException::class)
fun parseTime(time: String, includeSmallUnits: Boolean = true, timeZoneString: String): Long {
    var now = System.currentTimeMillis()
    if (!includeSmallUnits) {
        val timeZone = TimeZone.getTimeZone(timeZoneString)
        val offset = timeZone.getOffset(now)
        now += offset // Bring to user's timezone
        now = Instant.ofEpochMilli(now).truncatedTo(ChronoUnit.DAYS).toEpochMilli().milliseconds.inWholeMilliseconds
        now -= offset // Bring back to UTC
    }
    val parts = time.split("\\s".toRegex()).filter { it.isNotEmpty() }
    var unixTime = now
    var i = 0
    while (i < parts.size) {
        if (parts[i].isBlank() || parts[i] == "and" || parts[i] == "&") {
            i++
            continue
        }
        val part = parts[i]
        // Check for number first and unit after
        // Units can have multiple forms
        // First strip out the number and get the remaining string
        var hasEncounteredNonNumber = false
        var numDigits = 0
        part.filter {
            if (it.isDigit() && !hasEncounteredNonNumber) {
                numDigits++
                return@filter true
            }
            if (it == '.' && !hasEncounteredNonNumber) {
                numDigits++
                return@filter true
            }
            hasEncounteredNonNumber = true
            return@filter false
        }
        val number = part.substring(0, numDigits)
        var unit = part.substring(numDigits)
        if (unit.isEmpty()) {
            // Get the next part as a unit if it exists
            if (i + 1 < parts.size) {
                val nextPart = parts[i + 1]
                if (nextPart.isNotEmpty()) {
                    i++
                }
                unit = nextPart
            } else {
                throw DateTimeParseException("Invalid unit", unit, 0)
            }
        }
        val unitTime = parseUnit(unit)
        unixTime += number.toLong() * unitTime
        i++
    }
    return unixTime
}

fun parseUnit(unit: String): Long {
    return when (unit) {
        "s", "sec", "second", "seconds" -> 1000L
        "m", "min", "minute", "minutes" -> 1000L * 60
        "h", "hr", "hour", "hours" -> 1000L * 60 * 60
        "d", "day", "days" -> 1000L * 60 * 60 * 24
        "w", "week", "weeks" -> 1000L * 60 * 60 * 24 * 7
        "mo", "month", "months" -> 1000L * 60 * 60 * 24 * 30
        "y", "year", "years" -> 1000L * 60 * 60 * 24 * 365
        else -> throw DateTimeParseException("Invalid unit", unit, 0)
    }
}

@Throws(DateTimeParseException::class)
fun parseClockTime(time: String): Long {
    val parts = time.split(":")
    if (parts.size != 2) {
        throw DateTimeParseException("Invalid time format", time, 0)
    }
    try {
        var hours = parts[0].toLong()
        if (parts[1].contains("[a-zA-Z]+".toRegex())) {
            // Check if it is am/pm and adjust hours accordingly.
            // if there is none we assume 24 hour time
            if (parts[1].contains("am", true)) {
                if (hours > 12L) {
                    throw DateTimeParseException("Invalid time format", time, 0)
                }
                if (hours == 12L) {
                    hours = 0L
                }
            } else if (parts[1].contains("pm", true)) {
                if (hours > 12L) {
                    throw DateTimeParseException("Invalid time format", time, 0)
                }
                if (hours != 12L) {
                    hours += 12L
                }
            }
        }
        if (hours > 23L) {
            throw DateTimeParseException("Invalid time format", time, 0)
        }
        val minutes = parts[1].filter { it.isDigit() }.toLong()
        return hours * 60 * 60 * 1000L + minutes * 60 * 1000L
    } catch (e: NumberFormatException) {
        throw DateTimeParseException("Invalid time format", time, 0)
    }
}



