package tech.trip_kun.sinon.listeners

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import org.reflections.Reflections
import tech.trip_kun.sinon.*
import tech.trip_kun.sinon.annotations.ListenerClass
import tech.trip_kun.sinon.annotations.ListenerConstructor
import tech.trip_kun.sinon.annotations.ListenerIntents
import tech.trip_kun.sinon.command.Command
import tech.trip_kun.sinon.data.DatabaseException
import tech.trip_kun.sinon.exception.CommandExitException

private lateinit var commandListener: CommandListener

@ListenerClass
@ListenerIntents(GatewayIntent.MESSAGE_CONTENT)
class CommandListener @ListenerConstructor constructor(private val jda: JDA) : ListenerAdapter() {
    private val commands: HashMap<String, Command> = HashMap()
    init {
        Logger.info("CommandListener initialized")
        val reflections = Reflections("tech.trip_kun.sinon")
        reflections.getSubTypesOf(Command::class.java).forEach {
            Logger.info("Found command: ${it.simpleName}")
            it.constructors.forEach { constructor ->
                try {
                   val command = constructor.newInstance(jda) as Command
                    commands[command.getName()] = command
                } catch (e: Exception) {
                    addEmergencyNotification(EmergencyNotification("Failed to load command: ${it.simpleName}", 10, e.stackTraceToString()))
                }
            }
        }
        commandListener = this
    }
    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) {
            return
        }
        val message = event.message.contentRaw
        if (message.startsWith(getConfig().discordSettings.prefix)) {
            val command = message.substring(getConfig().discordSettings.prefix.length).split(" ")[0]
            try {
                if (commands.containsKey(command)) {
                    commands[command]!!.handler(event)
                }
            } catch (e: CommandExitException) {
                event.channel.sendMessage(e.message!!).queue()
            } catch (e: DatabaseException) {
                event.channel.sendMessage("Something went wrong with the database").queue()
            } catch (e: Exception) {
                addEmergencyNotification(
                    EmergencyNotification(
                        "Command Exception: ${command}",
                        10,
                        e.stackTraceToString()
                    )
                )
            }
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val command = event.name
        if (commands.containsKey(command)) {
            try {
                event.deferReply().queue()
                commands[command]!!.handler(event)
            } catch (e: CommandExitException) {
                event.hook.sendMessage(e.message!!).queue()
            } catch (e: DatabaseException) {
                event.hook.sendMessage("Something went wrong with the database").queue()
            } catch (e: Exception) {
                addEmergencyNotification(
                    EmergencyNotification(
                        "Command Exception: ${command}",
                        10,
                        e.stackTraceToString()
                    )
                )
            }
        }
    }
    fun getCommands(): Map<String,Command> {
        return commands
    }
}

fun getCommandListener(): CommandListener {
    return commandListener
}
