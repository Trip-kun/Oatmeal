package tech.trip_kun.sinon.command

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.trip_kun.sinon.data.runSQLUntilMaxTries
import java.time.DateTimeException
import java.time.ZoneId
import java.time.zone.ZoneRulesException

class SetTimezone(private val jda: JDA) : Command() {
    init {
        val name = "settimezone"
        val description =
            "Sets your timezone to a specified timezone. just use no arguments to see your current timezone"
        addArgument(Argument(name, description, true, ArgumentType.COMMAND, null))
        addArgument(Argument("timezone", "The timezone you want to set", false, ArgumentType.WORD, null))
    }

    override fun getCategory(): CommandCategory {
        return CommandCategory.ESSENTIAL
    }

    override suspend fun handler(event: MessageReceivedEvent) {
        val arguments = parseArguments(event)
        var timezone: String? = null
        if (arguments.isNotEmpty()) {
            timezone = arguments[0].getStringValue()
        }
        if (timezone?.isEmpty() == true) {
            timezone = null
        }
        val user = event.author
        val embedBuilders = commonWork(user, timezone)
        for (embedBuilder in embedBuilders) {
            event.channel.sendMessageEmbeds(embedBuilder.build()).await()
        }
    }

    override suspend fun handler(event: SlashCommandInteractionEvent) {
        val arguments = parseArguments(event)
        var timezone: String? = null
        if (arguments.isNotEmpty()) {
            timezone = arguments[0].getStringValue()
        }
        if (timezone?.isEmpty() == true) {
            timezone = null
        }
        val user = event.user
        val embedBuilders = commonWork(user, timezone)
        for (embedBuilder in embedBuilders) {
            event.hook.sendMessageEmbeds(embedBuilder.build()).await()
        }
    }

    private fun commonWork(user: User, timezone: String?): Array<EmbedBuilder> {
        if (timezone == null) {
            val embedBuilders: ArrayList<EmbedBuilder> = arrayListOf()
            var embedBuilder = EmbedBuilder()
            embedBuilder.setTitle("Here are the available timezones: ")
            embedBuilder.setDescription("You can set your timezone by using the command `/settimezone <timezone>`")
            val timezones = ZoneId.getAvailableZoneIds().toTypedArray()
            var outString = ""
            var totalLength = 0
            for (i in timezones.indices) {

                if ((outString + timezones[i] + ",").length > 1024) {
                    if (totalLength > 4000) {
                        embedBuilders.add(embedBuilder)
                        embedBuilder = EmbedBuilder()
                        embedBuilder.setTitle("Here are the available timezones: ")
                        embedBuilder.setDescription("You can set your timezone by using the command `/settimezone <timezone>`")
                        totalLength = 0
                    }
                    embedBuilder.addField("Timezones: ", outString, false)
                    outString = ""
                }
                totalLength += timezones[i].length
                outString += timezones[i] + ","
            }
            embedBuilders.add(embedBuilder)
            return embedBuilders.toTypedArray()
        } else {
            val embedBuilder = EmbedBuilder()
            try {
                val zid = ZoneId.of(timezone)
                var userObject: tech.trip_kun.sinon.data.entity.User? = null
                runSQLUntilMaxTries { userObject = tech.trip_kun.sinon.data.getUserDao().queryForId(user.idLong) }
                userObject?.timeZone = zid.id
                if (userObject != null) {
                    runSQLUntilMaxTries { tech.trip_kun.sinon.data.getUserDao().createOrUpdate(userObject) }
                }
                embedBuilder.setTitle("Timezone Set")
                embedBuilder.setDescription("Your timezone has been set to $timezone")

            } catch (e: DateTimeException) {
                embedBuilder.setTitle("Invalid Timezone")
                embedBuilder.setDescription("The timezone you provided is invalid. Please use the command `/settimezone` to see the available timezones")
            } catch (e: ZoneRulesException) {
                embedBuilder.setTitle("Invalid Timezone")
                embedBuilder.setDescription("The timezone you provided is invalid. Please use the command `/settimezone` to see the available timezones")
            }
            return arrayOf(embedBuilder)
        }
    }
}