package tech.trip_kun.sinon.listeners

import ch.qos.logback.classic.Logger
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import org.reflections.Reflections
import tech.trip_kun.sinon.Config
import tech.trip_kun.sinon.EmergencyNotification
import tech.trip_kun.sinon.addEmergencyNotification
import tech.trip_kun.sinon.annotations.ListenerClass
import tech.trip_kun.sinon.annotations.ListenerConstructor
import tech.trip_kun.sinon.annotations.ListenerIntents
import tech.trip_kun.sinon.command.Command
import tech.trip_kun.sinon.exception.CommandExitException
private lateinit var commandListener: CommandListener;
@ListenerClass
@ListenerIntents(GatewayIntent.MESSAGE_CONTENT)
class CommandListener @ListenerConstructor constructor(private val jda: JDA) : ListenerAdapter() {
    private val commands: HashMap<String, Command> = HashMap()
    init {
        println("CommandListener initialized")
        val reflections = Reflections("tech.trip_kun.sinon")
        val logger = org.slf4j.LoggerFactory.getLogger(Logger::class.java)
        reflections.getSubTypesOf(Command::class.java).forEach {
            logger.info("Found command: ${it.simpleName}")
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
        if (message.startsWith(Config.getConfig().prefix)) {
            val command = message.substring(Config.getConfig().prefix.length).split(" ")[0]
            try {
                if (commands.containsKey(command)) {
                    commands[command]!!.handler(event)
                }
            } catch (e: CommandExitException) {
                event.channel.sendMessage(e.message!!).queue()
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
