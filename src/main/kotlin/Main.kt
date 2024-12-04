package tech.trip_kun.sinon

import tech.trip_kun.sinon.data.getBanEntryDao
import tech.trip_kun.sinon.data.runSQLUntilMaxTries

fun main(args: Array<String>) {
          getJDA() // Get the JDA instance
          startNotificationThread() // Start the notification thread
          loadJDA() // Load JDA (start the bot)
          runSQLUntilMaxTries { getBanEntryDao()  } // Load any db component which will trigger the rest of the components to load as well.
          Logger.info("Loaded all core components")
      }