package tech.trip_kun.sinon

import net.dv8tion.jda.api.utils.FileUpload
import okhttp3.internal.notify
import okhttp3.internal.wait
import java.util.concurrent.PriorityBlockingQueue

// This system will be used to send emergency notifications to admins.
// It will have a queue with Tasks that will be executed in order while accounting fot the priority and discord's rate limits.
// The system will be able to send messages to a specific channel, all admins, or both.
// This should trigger on events such as unexpected exceptions, database connection issues, or other critical issues.
// The system will have a configuration file that will allow the user to set the priority of each type of notification.

data class EmergencyNotification(
    val message: String,
    val priority: Int,
    val longMessage: String? = null
) : Comparable<EmergencyNotification> {
    override fun compareTo(other: EmergencyNotification): Int {
        return priority.compareTo(other.priority)
    }

}

private lateinit var notificationObject: Any

private val emergencyNotificationPriorityQueue: PriorityBlockingQueue<EmergencyNotification> = PriorityBlockingQueue()
private val jda = getJDA()
fun addEmergencyNotification(emergencyNotification: EmergencyNotification) {
    startNotificationThread()
    emergencyNotificationPriorityQueue.add(emergencyNotification)
    while (!::notificationObject.isInitialized) {
        // Hey kotlin, can we get a waitTillInitialized() extension function? :D
    }
    synchronized(notificationObject) {
        notificationObject.notify()
    }
}

private val config = getConfig().discordSettings
private val sendToAdmins = config.emergencyNotificationsForAdmins
private val sendToChannels = config.emergencyNotificationsForChannels
private val admins = config.admins
private val channels = config.channels
private var stopNotificationThread = false
private val notificationThread = Thread {
    notificationObject = Object()
    synchronized(notificationObject) {
        notificationObject.notify()
    }
    while (!stopNotificationThread) {
        while (emergencyNotificationPriorityQueue.isNotEmpty()) {
            val notification = emergencyNotificationPriorityQueue.poll()
            if (sendToAdmins) {
                admins.forEach { admin ->
                    jda.retrieveUserById(admin).queue { user ->
                        user.openPrivateChannel().queue { channel ->
                            channel.sendMessage(notification.message).queue() // We're actually fine with not using await here since there's basically nothing we can do if it fails anyway, and we don't want to block the thread
                            if (notification.longMessage != null) {
                                kotlin.io.path.createTempFile("longMessage", ".txt").toFile().apply {
                                    writeText(notification.longMessage)
                                }.apply{
                                    channel.sendFiles(FileUpload.fromData(this)).queue()
                                }
                            }
                        }
                    }
                }
            }
            if (sendToChannels) {
                channels.forEach { channel ->
                    jda.getTextChannelById(channel)?.sendMessage(notification.message)?.queue()
                    if (notification.longMessage != null) {
                        kotlin.io.path.createTempFile("longMessage", ".txt").toFile()
                            .apply { writeText(notification.longMessage) }.apply {
                            jda.getTextChannelById(channel)?.sendFiles(
                                FileUpload.fromData(this)
                            )?.queue()
                        }
                    }
                }
            }
        }
        if (emergencyNotificationPriorityQueue.isEmpty()) {
            try {
                synchronized(notificationObject) {
                    notificationObject.wait()
                }
            } catch (_: InterruptedException) { // We want the thread to be interrupted when a new notification is added
            }
        }
    }
}

fun startNotificationThread() {
    if (!notificationThread.isAlive)
        notificationThread.start()
}

fun stopNotificationThread() {
    startNotificationThread()
    stopNotificationThread = true
    synchronized(notificationObject) {
        notificationObject.notify()
    }
    notificationThread.join(1000)
    if (notificationThread.isAlive) {
        notificationThread.interrupt()
    }
}

fun getEmergencyNotificationStatus(): String {
    return "Emergency Notification System Status: Emergency Notification System is ${if (notificationThread.isAlive) "running" else "not running"}"
}