package tech.trip_kun.sinon

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.io.File
@OptIn(ExperimentalSerializationApi::class)
private val json = Json { encodeDefaults = true; prettyPrint = true; explicitNulls=false }
@Serializable
data class Config(
    val version: String = "alpha",
    val threadSettings: ThreadSettings = ThreadSettings(),
    val databaseSettings: DatabaseSettings = DatabaseSettings(),
    val discordSettings: DiscordSettings = DiscordSettings(),
) {
    init {
        require(version.isNotBlank()) { "version must be set" }
    }
}
@Serializable
data class ThreadSettings(
    val threadPoolSize: Int = 10,
    val threadSleep: Int = 100,
) {
    init {
        require(threadPoolSize > 0) { "threadPoolSize must be greater than 0" }
        require(threadSleep >= 0) { "threadSleep must be greater than or equal to 0" }
    }
}
@Serializable
data class DatabaseSettings(
    val databaseURL: String = "localhost", // The URL of the database
    val databaseUser: String = "user", // The user for the database
    val databasePassword: String = "pass", // The password for the database
    val databasePort: Int = 3306,
    val databaseName: String = "oatmeal", // The name of the database
    val databaseMaxRetries: Int = 3, // There will be 3 retries by default
    val databaseRetryDelay: Int = 1000, // 1 second
    val databaseMaxConnections: Int = 10, // 0 for unlimited
    val databaseMaxResponses: Int = 10,  // 0 for unlimited
    val databaseReloadTime: Int = 5*60*1000, // 5 minutes
) {
    init {
        require(databasePort in 1..65535) { "port must be between 1 and 65535" }
        require(databaseMaxRetries >= 0) { "databaseMaxRetries must be greater than or equal to 0" }
        require(databaseRetryDelay >= 0) { "databaseRetryDelay must be greater than or equal to 0" }
        require(databaseMaxConnections >= 0) { "databaseMaxConnections must be greater than or equal to 0" }
        require(databaseMaxResponses >= 0) { "databaseMaxResponses must be greater than or equal to 0" }
        require(databaseReloadTime >= 0) { "databaseReloadTime must be greater than or equal to 0" }
        require(databaseName.isNotBlank()) { "databaseName must be set" }
        require(databaseURL.isNotBlank()) { "databaseURL must be set" }
        require(databaseUser.isNotBlank()) { "databaseUser must be set" }
        require(databasePassword.isNotBlank()) { "databasePassword must be set" }
    }
}

@Serializable
data class DiscordSettings (
    val discordToken: String = "token",
    val prefix: String = "s!",
    val emergencyNotificationsForAdmins: Boolean = true,
    val emergencyNotificationsForChannels: Boolean = false,
    val admins: MutableList<Long> = mutableListOf(),
    val channels: MutableList<Long> = mutableListOf(),
) {
    init {
        require(discordToken.isNotBlank()) { "discordToken must be set" }
        require(prefix.isNotBlank()) { "prefix must be set" }
    }
}

private lateinit var config: Config
fun getConfig(): Config {
    if (!::config.isInitialized) {
        try { // First we see if the config exists and is valid JSON for the Config class.
            Logger.info("Reading config.json")
            val file = File("config.json")

            config = json.decodeFromString(file.readText())
        } catch (e: SerializationException) { // If the file is invalid JSON, we print an error and throw an exception, noting possible solutions.
            Logger.info("You may need to delete config.json and restart the server. This could be caused by a configuration update. If so, try renaming config.json to config.json.old, and restarting the server to create a new default config, then update the new config.json with your settings.")
            throw e
        } catch (e: Exception) { // If the file doesn't exist, we write a default config.
            config = Config() // Default config
            val file = File("config.json")
            try {
                Logger.info("Writing default config.json")
                file.writeText(json.encodeToString(config))
            } catch (e: Exception) { // If we can't write the default config, we print an error and throw an exception.
                Logger.info("Error writing default config: ${e.message}")
                throw e
            }
        }
    }
    return config
}