package io.zeko.restapi.core.utilities

import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


suspend fun Vertx.setTimerSuspend(duration: Long, block: suspend () -> Any) {
    val vertx = this
    vertx.setTimer(duration) {
        GlobalScope.launch(vertx.dispatcher()) {
            block.invoke()
        }
    }
}

suspend fun Vertx.runCronSuspend(scheduleBlock: () -> Long, block: suspend () -> Any) {
    val vertx = this
    val duration = scheduleBlock()

    vertx.setTimer(duration) {
        GlobalScope.launch(vertx.dispatcher()) {
            vertx.runCronSuspend(scheduleBlock, block)
            block.invoke()
        }
    }
}

fun Vertx.runCron(scheduleBlock: () -> Long, block: () -> Any) {
    val vertx = this
    val duration = scheduleBlock()

    vertx.setTimer(duration) {
        vertx.runCron(scheduleBlock, block)
        block.invoke()
    }
}
