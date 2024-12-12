package tech.trip_kun.sinon

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.EventListener
import org.reflections.Reflections
import tech.trip_kun.sinon.annotations.ListenerClass
import tech.trip_kun.sinon.annotations.ListenerConstructor
import tech.trip_kun.sinon.annotations.ListenerIntents
import java.lang.reflect.Constructor

private val config: Config = getConfig()
private lateinit var jda: JDA
val reflections: Reflections = Reflections("tech.trip_kun.sinon")
fun getJDA(): JDA {
    if (::jda.isInitialized) {
        return jda
    }
    val jdaBuilder = JDABuilder.createDefault(config.discordSettings.discordToken)
    addListenerIntents(jdaBuilder)
    jda = jdaBuilder.build().awaitReady()
    return jda
}

fun loadJDA() {
    getJDA()
    addListeners(jda)
}

private fun addListeners(jda: JDA) {
    reflections.getSubTypesOf(EventListener::class.java).filter { it.isAnnotationPresent(ListenerClass::class.java) }
        .forEach { listener ->
            listener.constructors.filter { it.isAnnotationPresent(ListenerConstructor::class.java) }
                .forEach { constructor ->
                    addListener(jda, listener, constructor)
                }
        }
}

private fun addListenerIntents(jdaBuilder: JDABuilder) {
    reflections.getSubTypesOf(EventListener::class.java).filter { it.isAnnotationPresent(ListenerClass::class.java) }
        .forEach { listener ->
            listener.constructors.filter { it.isAnnotationPresent(ListenerConstructor::class.java) }.forEach { _ ->
                addListenerIntent(jdaBuilder, listener)
            }
        }
}

private fun addListener(jda: JDA, listener: Class<out EventListener>, constructor: Constructor<*>) {
    try {
        jda.addEventListener(constructor.newInstance(jda))
    } catch (e: Exception) {
        Logger.error("Failed to load listener: ${listener.simpleName}", e)
    }
}

private fun addListenerIntent(jdaBuilder: JDABuilder, listener: Class<out EventListener>) {
    listener.annotations.filterIsInstance<ListenerIntents>().forEach {
        jdaBuilder.enableIntents(it.gatewayIntent)
    }
}

fun getJDAStatus(): String {
    var status = "JDA Status: "
    status += if (::jda.isInitialized) {
        "Initialized"
    } else {
        "Not Initialized"
    }
    // Also check if the JDA is connected
    status += " and is in state: ${jda.status}"
    return status
}