package io.zeko.restapi.core.cron

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import io.vertx.core.Vertx
import org.slf4j.Logger
import io.zeko.restapi.core.utilities.runCron
import io.zeko.restapi.core.utilities.runCronSuspend
import java.time.Duration
import java.time.ZonedDateTime


open class CronRunner(val vertx: Vertx, val logger: Logger) {
    var cronParser: CronParser? = null

    fun init(cronType: CronType = CronType.UNIX): CronRunner {
        val cronDef = CronDefinitionBuilder.instanceDefinitionFor(cronType)
        cronParser = CronParser(cronDef)
        return this
    }

    fun schedule(expression: String): () -> Long {
        return {
            val now = ZonedDateTime.now()
            val executionTime = ExecutionTime.forCron(cronParser?.parse(expression))
            val timeToNextExec = executionTime.timeToNextExecution(now)
            lateinit var duration: Duration
            timeToNextExec.map { duration = it }
            duration.toMillis()
        }
    }

    fun run(schedule: String, block: () -> Any) {
        vertx.runCron(schedule(schedule), block)
    }

    suspend fun runSuspend(schedule: String, block: suspend () -> Any) {
        vertx.runCronSuspend(schedule(schedule), block)
    }

    fun run(scheduleBlock: () -> Long, block: () -> Any) {
        vertx.runCron(scheduleBlock, block)
    }

    suspend fun runSuspend(scheduleBlock: () -> Long, block: suspend () -> Any) {
        vertx.runCronSuspend(scheduleBlock, block)
    }
}
