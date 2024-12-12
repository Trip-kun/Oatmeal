package tech.trip_kun.sinon

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

private val threadPool = Executors.newCachedThreadPool()
private val dispatcher = threadPool.asCoroutineDispatcher()
fun getDispatcher() = dispatcher

fun shutdownDispatcher() {
    threadPool.shutdownNow()
    threadPool.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)
}