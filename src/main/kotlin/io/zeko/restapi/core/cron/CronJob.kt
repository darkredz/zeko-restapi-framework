package io.zeko.restapi.core.cron

import com.cronutils.model.CronType
import io.vertx.core.Vertx
import org.slf4j.Logger

open abstract class CronJob(val vertx: Vertx, val logger: Logger) {
    var cronRunner: CronRunner? = null

    open fun init(): CronJob {
        cronRunner = CronRunner(vertx, logger).init()
        return this
    }

    open fun init(cronType: CronType): CronJob {
        cronRunner = CronRunner(vertx, logger).init(cronType)
        return this
    }

    open fun setRunner(cronRunner: CronRunner): CronJob {
        this.cronRunner = cronRunner
        return this
    }

    open fun getRunner(): CronRunner? {
        return cronRunner
    }
}
