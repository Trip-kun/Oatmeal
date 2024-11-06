package tech.trip_kun.sinon

class Main {
   companion object {
      @JvmStatic
      fun main(args: Array<String>) {
         getJDA()
         startNotificationThread()
         loadJDA()

      }
   }
}