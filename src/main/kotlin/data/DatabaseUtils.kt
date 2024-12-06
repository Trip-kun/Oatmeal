@file:OptIn(DelicateCoroutinesApi::class)

package tech.trip_kun.sinon.data

import com.j256.ormlite.dao.Dao
import com.j256.ormlite.dao.DaoManager
import com.j256.ormlite.jdbc.JdbcPooledConnectionSource
import com.j256.ormlite.table.TableUtils
import kotlinx.coroutines.*
import tech.trip_kun.sinon.EmergencyNotification
import tech.trip_kun.sinon.Logger
import tech.trip_kun.sinon.addEmergencyNotification
import tech.trip_kun.sinon.data.entity.BanEntry
import tech.trip_kun.sinon.data.entity.DatabaseInfo
import tech.trip_kun.sinon.data.entity.Reminder
import tech.trip_kun.sinon.data.entity.User
import tech.trip_kun.sinon.getConfig
import java.sql.SQLException

private var userDao: Dao<User, Long>? = null
private var reminderDao: Dao<Reminder, Long>? = null
private var banEntryDao: Dao<BanEntry, Int>? = null
private lateinit var databaseInfoDao: Dao<DatabaseInfo, Int>
private val DATABASE_VERSION = 1

//Complete
//Need to make a try-3-times method to use for all database calls
// If all 3 calls fail we throw a marked runtime exception and notify the admins
// After this the database will attempt to reconnect every 5 minutes
// If the database reconnects we will notify the admins and remove the forced shutdown
// We will never force a full shutdown of the bot, but we will force a shutdown of the database
// This is to ensure that the bot can still function without the database
private var databaseEnabled = false
val config = getConfig()
val globalDispatcher = newSingleThreadContext("GlobalDispatcher")

class DatabaseException(message: String) : RuntimeException(message) {
    private var hasBeenNotified = false
    fun hasBeenNotified(): Boolean {
        return hasBeenNotified
    }

    fun setNotified() {
        hasBeenNotified = true
    }
}

private var dbTries = 0
private var databaseDoNotTryAgain = false
private var makeDatabaseJob: Job? = null
private var isJobLaunched = false
private fun blockingRunSQLUntilMaxTries(action: () -> Unit): DatabaseException? {
    try {
        if (databaseDoNotTryAgain) {
            throw DatabaseException("Database is disabled and will not be tried again")
        }
        if (isJobLaunched || makeDatabaseJob?.isActive == true) {
            throw DatabaseException("Database is currently waiting to reconnect")
        }
        var tries = 0
        var success = false
        var dbexception: DatabaseException? = null
        while (!success) {
            try {
                try {
                    action()
                    success = true
                } catch (e: DatabaseException) {
                    tries =
                        config.databaseSettings.databaseMaxRetries // This will force the loop to exit and work on the reconnect. Especially useful for initial database load
                    dbexception = e
                    throw SQLException(e)
                }
            } catch (e: SQLException) {
                if ((tries >= config.databaseSettings.databaseMaxRetries)) {
                    databaseEnabled = false
                    if (makeDatabaseJob == null || makeDatabaseJob!!.isCompleted) {
                        makeDatabaseJob =
                            GlobalScope.launch(globalDispatcher) { reloadDatabase() }
                    }
                }
                if (dbexception == null || !dbexception.hasBeenNotified()) {
                    addEmergencyNotification(
                        EmergencyNotification(
                            "Failed to run SQL after $tries tries",
                            1,
                            e.stackTraceToString()
                        )

                    )
                    dbexception?.setNotified()
                }
                if (tries < config.databaseSettings.databaseMaxRetries) {
                    Logger.warn("Retrying in ${config.databaseSettings.databaseRetryDelay}ms")
                    Thread.sleep(config.databaseSettings.databaseRetryDelay.toLong())
                } else {
                    if (dbexception != null) {
                        throw dbexception
                    }
                    throw DatabaseException("Failed to run SQL after $tries tries")
                }
                tries++
            }
        }
    } catch (e: DatabaseException) {
        return e
    }
    return null
}

fun runSQLUntilMaxTries(action: () -> Unit) {
    val exception = blockingRunSQLUntilMaxTries(action)
    if (exception != null) {
        throw exception
    }
}

private fun loadDatabase() {
    if (databaseDoNotTryAgain) {
        throw DatabaseException("Database is disabled and will not be tried again")
    }
    if (isJobLaunched) {
        throw DatabaseException("Database is currently waiting to reconnect")
    }
    try {
        val connectionSource = JdbcPooledConnectionSource(
            "jdbc:mariadb://${config.databaseSettings.databaseURL}:${config.databaseSettings.databasePort}/${config.databaseSettings.databaseName}",
            config.databaseSettings.databaseUser,
            config.databaseSettings.databasePassword
        )
        connectionSource.setMaxConnectionsFree(config.databaseSettings.databaseMaxConnections)
        connectionSource.initialize()
        TableUtils.createTableIfNotExists(connectionSource, DatabaseInfo::class.java)
        databaseInfoDao = DaoManager.createDao(connectionSource, DatabaseInfo::class.java)
        val databaseInfo = databaseInfoDao.queryForId(0)
        if (databaseInfo == null) {
            val newDatabaseInfo = DatabaseInfo()
            newDatabaseInfo.currentVersion = DATABASE_VERSION
            newDatabaseInfo.firstVersion = DATABASE_VERSION
            databaseInfoDao.create(newDatabaseInfo)
        } else {
            if (databaseInfo.currentVersion < DATABASE_VERSION) {
                upgrade(databaseInfo.currentVersion, DATABASE_VERSION)
                databaseInfo.currentVersion = DATABASE_VERSION
                databaseInfoDao.update(databaseInfo)
            }
        }
        TableUtils.createTableIfNotExists(connectionSource, User::class.java)
        val userDao2: Dao<User, Long> = DaoManager.createDao(connectionSource, User::class.java)
        userDao2.setObjectCache(true)
        userDao = userDao2
        TableUtils.createTableIfNotExists(connectionSource, Reminder::class.java)
        val reminderDao2: Dao<Reminder, Long> = DaoManager.createDao(connectionSource, Reminder::class.java)
        reminderDao2.setObjectCache(true)
        reminderDao = reminderDao2
        TableUtils.createTableIfNotExists(connectionSource, BanEntry::class.java)
        val banEntryDao2: Dao<BanEntry, Int> = DaoManager.createDao(connectionSource, BanEntry::class.java)
        banEntryDao2.setObjectCache(true)
        banEntryDao = banEntryDao2

        databaseEnabled = true
        dbTries = 0
    } catch (e: SQLException) {
        databaseEnabled = false
        addEmergencyNotification(
            EmergencyNotification(
                "Database failed to load on try ${dbTries + 1} of ${config.databaseSettings.databaseMaxRetries}",
                1,
                e.stackTraceToString()
            )
        )
        throw DatabaseException("Database failed to load on try ${dbTries + 1} of ${config.databaseSettings.databaseMaxRetries}")
    }
}

fun getUserDao(): Dao<User, Long> {
    if (!databaseEnabled && !databaseDoNotTryAgain) {

        runSQLUntilMaxTries { loadDatabase() }
    }
    if (userDao == null) {
        throw DatabaseException("User DAO is null")
    }
    return userDao!!
}

fun getReminderDao(): Dao<Reminder, Long> {
    if (!databaseEnabled && !databaseDoNotTryAgain) {
        runSQLUntilMaxTries { loadDatabase() }
    }
    if (reminderDao == null) {
        throw DatabaseException("Reminder DAO is null")
    }
    return reminderDao!!
}

fun getBanEntryDao(): Dao<BanEntry, Int> {
    if (!databaseEnabled && !databaseDoNotTryAgain) {
        runSQLUntilMaxTries { loadDatabase() }
    }
    if (banEntryDao == null) {
        throw DatabaseException("BanEntry DAO is null")
    }
    return banEntryDao!!
}

private fun upgrade(startVersion: Int, endVersion: Int) {
    if (startVersion < endVersion) {
        when (startVersion) {
            1 -> {
                // Upgrade from version 1 to version 2 We are currently at version 1 so we don't need to do anything
            }
        }
        upgrade(startVersion + 1, endVersion)
    }
}


private suspend fun reloadDatabase() {
    if (databaseDoNotTryAgain || dbTries >= config.databaseSettings.databaseMaxRetries) {
        return
    }
    while (!databaseEnabled) {
        isJobLaunched = true
        delay(config.databaseSettings.databaseReloadTime.toLong())
        isJobLaunched = false
        if (databaseDoNotTryAgain) {
            break
        }
        try {
            isJobLaunched = false
            loadDatabase()
            isJobLaunched = true
            addEmergencyNotification(
                EmergencyNotification(
                    "Database reconnected after ${config.databaseSettings.databaseMaxRetries} tries",
                    1,
                    null
                )
            )
            dbTries = 0
            databaseDoNotTryAgain = false
            databaseEnabled = true
        } catch (e: DatabaseException) {
            isJobLaunched = true
            addEmergencyNotification(
                EmergencyNotification(
                    "Database failed to load on try ${dbTries + 2} of ${config.databaseSettings.databaseMaxRetries}",
                    1,
                    e.stackTraceToString()

                )
            )
            isJobLaunched = true
            dbTries++
        }
        if (databaseEnabled) {
            break
        }
        if (dbTries + 1 >= config.databaseSettings.databaseMaxRetries) {
            addEmergencyNotification(
                EmergencyNotification(
                    "Database failed to load on try ${dbTries} of ${config.databaseSettings.databaseMaxRetries}. It will remain shutdown and will not be tried again",
                    1,
                    null
                )
            )
            databaseDoNotTryAgain = true
        }
    }
    isJobLaunched = false
}

private fun closeDatabase() {
    if (databaseEnabled) {
        userDao = null
        reminderDao = null
        banEntryDao = null
        databaseDoNotTryAgain = true
        databaseEnabled = false
        dbTries = config.databaseSettings.databaseMaxRetries + 1
        globalDispatcher.close()
    } else {
        databaseDoNotTryAgain = true
    }
}

fun getDatabaseStatus(): String {
    var start = "Database Status: Database is "
    start += if (databaseEnabled) {
        "enabled"
    } else {
        "disabled"
    }
    // Also say if we are trying to reconnect or permanently disabled
    start += if (databaseDoNotTryAgain) {
        " and will not be tried again"
    } else if (isJobLaunched) {
        " and is currently trying to reconnect"
    } else {
        " and is not trying to reconnect (this should only occur when the database is enabled)"
    }
    return start
}