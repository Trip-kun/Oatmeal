package tech.trip_kun.sinon.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.trip_kun.sinon.data.getDatabaseStatus
import tech.trip_kun.sinon.getEmergencyNotificationStatus
import tech.trip_kun.sinon.getJDAStatus

class Status(private val jda: JDA): Command() {
    private val name: String
    private val description: String
    init {
        name = "status"
        description = "Get the status of the bot"
        addArgument(Argument(name,description,true, ArgumentType.COMMAND, null))
        initialize(jda)
    }

    override fun getCategory(): CommandCategory {
        return CommandCategory.ESSENTIAL
    }

    override suspend fun handler(event: MessageReceivedEvent) {
        event.channel.sendMessage(getStatus()).queue()
    }

    override suspend fun handler(event: SlashCommandInteractionEvent) {
        event.hook.sendMessage(getStatus()).queue()
    }
    private fun getStatus(): String {
        // We have multiple status checks to do here
        var status = "Status: \n"
        status += getJDAStatus() + "\n"
        status += getDatabaseStatus() + "\n"
        status += getEmergencyNotificationStatus() + "\n"
        return status

    }
}