package io.zeko.restapi.core.cron

import io.vertx.core.Vertx
import org.slf4j.Logger

open abstract class CronSchema(val vertx: Vertx, val logger: Logger) {
    open suspend fun handleJobs(runner: CronRunner) {}
}
