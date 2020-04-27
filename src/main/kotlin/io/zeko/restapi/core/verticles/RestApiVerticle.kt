package io.zeko.restapi.core.verticles

import io.vertx.core.logging.Logger
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import io.zeko.restapi.core.RouteSchema
import io.zeko.restapi.core.cron.CronRunner
import io.zeko.restapi.core.cron.CronSchema
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.coroutines.CoroutineContext

open abstract class ZekoVerticle : CoroutineVerticle() {
    fun Route.koto(fn: suspend (RoutingContext) -> Unit) {
        handler { ctx ->
            launch(ctx.vertx().dispatcher()) {
                try {
                    fn(ctx)
                } catch (e: Exception) {
                    ctx.fail(e)
                }
            }
        }
    }

    fun koto(route: Route, fn: suspend (RoutingContext, CoroutineContext) -> Unit) {
        route.handler { ctx ->
            launch(ctx.vertx().dispatcher()) {
                try {
                    fn(ctx, this.coroutineContext)
                } catch (e: Exception) {
                    ctx.fail(e)
                }
            }
        }
    }


    suspend fun startCronJobs(schema: CronSchema, runner: CronRunner) {
        schema.handleJobs(runner)
    }

    suspend fun startCronJobs(schema: CronSchema, logger: Logger) {
        val runner = CronRunner(vertx, logger).init()
        schema.handleJobs(runner)
    }

    suspend fun startCronJobs(schemaClass: String, logger: Logger) {
        val runner = CronRunner(vertx, logger).init()
        startCronJobs(schemaClass, runner, logger)
    }

    suspend fun startCronJobs(schemaClass: String, runner: CronRunner, logger: Logger) {
        try {
            val cls = Class.forName(schemaClass)
            val constructor = cls.constructors.first()
            constructor.isAccessible = true
            val schema = constructor.newInstance(vertx, logger)

            if (schema is CronSchema) {
                schema.handleJobs(runner)
            }
        } catch (err: Exception) {
            logger.error("Cron class does not exists or is invalid: $schemaClass")
        }
    }

    fun bindRoutes(schema: RouteSchema, router: Router, logger: Logger) {
        schema.handleRoutes(router, logger, this::koto)
    }

    fun bindRoutes(schemaClass: String, router: Router, logger: Logger) {
        try {
            val cls = Class.forName(schemaClass)
            val constructor = cls.constructors.first()
            constructor.isAccessible = true
            val schema = constructor.newInstance(vertx)

            if (schema is RouteSchema) {
                schema.handleRoutes(router, logger, this::koto)
            }
        } catch (err: Exception) {
            logger.error("Route class does not exists or is invalid: $schemaClass")
        }
    }

    fun handleRuntimeError(router: Router, logger: Logger, errorMessage: String = "Internal Server Error", errorLogPrefix: String = "RUNTIME_ERROR") {
        router.route().failureHandler { failureRoutingContext ->
            val statusCode = if (failureRoutingContext.statusCode() > 0) failureRoutingContext.statusCode() else 500
            var response = failureRoutingContext.response()

            if (statusCode == 503) {
                response.setStatusCode(503).end("Service Unavailable")
                return@failureHandler
            }

            val err = failureRoutingContext.failure()

            if (err != null) {
                val path = failureRoutingContext.normalisedPath()
                val sw = StringWriter()
                err.printStackTrace(PrintWriter(sw))

                logger.error("$errorLogPrefix $path ${err.message}")
                logger.error(sw.toString())
            }

            // Status code will be 500 for the RuntimeException
            response.setStatusCode(500).end(errorMessage)
        }
    }
}
