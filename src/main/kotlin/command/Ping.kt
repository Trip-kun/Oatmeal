package tech.trip_kun.sinon.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class Ping(private val jda: JDA): Command() {
    private val name: String = "ping"
    private val description: String = "Pong!"

    init {
        addArgument(Argument(name,description,true, ArgumentType.COMMAND, null))
        initialize(jda)
    }

    override fun getCategory(): CommandCategory {
        return CommandCategory.ESSENTIAL
    }

    override fun handler(event: MessageReceivedEvent) {
        event.channel.sendMessage("Pong!").queue()
    }

    override fun handler(event: SlashCommandInteractionEvent) {
        event.hook.sendMessage("Pong!").queue()
    }

}