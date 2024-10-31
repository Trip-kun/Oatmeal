package tech.trip_kun.sinon

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

val GsonBuilder: GsonBuilder = GsonBuilder()
val gson: Gson = GsonBuilder.create()
class Config {
    inner class ThreadSettings{
        val threadLimit: Int = 50
        val threadSleep: Int = 100
    }
    inner class DatabaseSettings {
        val databaseURL: String = ""
        val databaseUser: String = ""
        val databasePassword: String = ""
        val databasePort: Int = 3306
        val databaseName: String = ""
        val databaseMaxRetries: Int = 3
        val databaseRetryDelay: Int = 1000
        val databaseMaxConnections: Int = 10
        val databaseMaxResponses: Int = 10
    }
    var threadSettings: ThreadSettings = ThreadSettings()
    var databaseSettings: DatabaseSettings = DatabaseSettings()
    val version: Int = 1
    val discordToken: String = ""
    val prefix: String = "s!"
    val emergencyNotificationsForAdmins: Boolean = true
    val emergencyNotificationsForChannels: Boolean = false
    val admins = mutableListOf<Long>()
    val channels = mutableListOf<Long>()
    companion object {
        @JvmStatic
        @Transient
        private var config: Config? = null
        @JvmStatic
        fun getConfig(): Config {
            if (config == null) {
                try {
                    config = gson.fromJson(Files.readString(Path.of("config.json")), Config::class.java)
                } catch (e: IOException) {
                    config = Config()
                    Files.writeString(Path.of("config.json"), gson.toJson(config))
                }
            }
            return config!!
        }
    }
}