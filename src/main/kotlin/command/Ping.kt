package tech.trip_kun.sinon.command

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.trip_kun.sinon.exception.CommandExitException

class Ping(private val jda: JDA) : Command() {
    private val name: String = "ping"
    private val description: String = "Pong!"

    init {
        addArgument(Argument(name, description, true, ArgumentType.COMMAND, null))
        initialize(jda)
    }

    override fun getCategory(): CommandCategory {
        return CommandCategory.ESSENTIAL
    }

    override suspend fun handler(event: MessageReceivedEvent) {
        val message = try { event.channel.sendMessage("Pong!").await() } catch (e: Exception) { throw CommandExitException("Failed to send message") }
        val time = message.timeCreated.toInstant().toEpochMilli() - event.message.timeCreated.toInstant().toEpochMilli()
        message.editMessage("Pong! (${time}ms)").await()
    }

    override suspend fun handler(event: SlashCommandInteractionEvent) {
        val message = try { event.channel.sendMessage("Pong!").await() } catch (e: Exception) { throw CommandExitException("Failed to send message") }
        val time = message.timeCreated.toInstant().toEpochMilli() - event.interaction.timeCreated.toInstant().toEpochMilli()
        message.editMessage("Pong! (${time}ms)").await()
    }

}