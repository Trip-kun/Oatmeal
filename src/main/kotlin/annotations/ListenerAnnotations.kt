package tech.trip_kun.sinon.annotations

import net.dv8tion.jda.api.requests.GatewayIntent

@Target(AnnotationTarget.CONSTRUCTOR)
annotation class ListenerConstructor()
@Target(AnnotationTarget.CLASS)
annotation class ListenerClass()
@Repeatable
@Target(AnnotationTarget.CLASS)
annotation class ListenerIntents(val gatewayIntent: GatewayIntent)