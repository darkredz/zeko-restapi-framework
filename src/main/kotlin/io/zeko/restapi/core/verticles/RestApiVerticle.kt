package io.zeko.restapi.core.verticles

import io.vertx.core.json.JsonObject
import io.vertx.core.logging.Logger
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import io.zeko.restapi.core.RouteSchema
import io.zeko.restapi.core.cron.CronRunner
import io.zeko.restapi.core.cron.CronSchema
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZonedDateTime
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

    fun bindRoutes(schema: RouteSchema, router: Router, logger: Logger, useCamelCaseResponse: Boolean = false) {
        if (useCamelCaseResponse) {
            router.route().handler {
                it.put("useCamelCaseResponse", true)
                it.next()
            }
        }
        schema.handleRoutes(router, logger, this::koto)
    }

    fun bindRoutes(schemaClass: String, router: Router, logger: Logger, useCamelCaseResponse: Boolean = false) {
        if (useCamelCaseResponse) {
            router.route().handler {
                it.put("useCamelCaseResponse", true)
                it.next()
            }
        }
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

    fun trackResponseTime(router: Router) {
        router.route("/*").handler {
            val startMs = System.currentTimeMillis()
            it.put("start_ms", startMs)
            it.next()
        }
    }

    fun withAccessLog(router: Router, logger: Logger) {
        router.route("/*").handler {
            val logMsg = generateAccessLogBody(it)
            logger.info(logMsg.encode())
            if (!it.response().ended()) it.next()
        }
    }

    fun handleRuntimeError(
        router: Router,
        logger: Logger,
        asJson: Boolean = false,
        errorMessage: String = "Internal Server Error",
        errorLogPrefix: String = "RUNTIME_ERROR"
    ) {
        router.route().failureHandler {
            val statusCode = if (it.statusCode() > 0) it.statusCode() else 500
            var response = it.response()
            val path = it.normalizedPath()
            val err = it.failure()

            if (err != null) {
                val sw = StringWriter()
                err.printStackTrace(PrintWriter(sw))
                val errMsgStack = sw.toString()

                if (!asJson) {
                    logger.error("$errorLogPrefix $statusCode $path  ${err.message}")
                    logger.error(errMsgStack)
                } else {
                    val logMsg = generateAccessLogBody(it)
                    logMsg.put("error", true)
                    logMsg.put("error_msg", err.toString())
                    logMsg.put("error_stack", errMsgStack)
                    logger.error(logMsg.encode())
                }
            } else {
                if (statusCode == 503) {
                    response.setStatusCode(503).end("Service Unavailable")
                    if (!asJson) {
                        logger.error("SERVICE_UNAVAILABLE 503 $path")
                    } else {
                        val logMsg = generateAccessLogBody(it)
                        logMsg.put("error", true)
                        logMsg.put("error_msg", "Service Unavailable")
                        logger.error(logMsg.encode())
                    }
                }
            }

            // Status code will be 500 for the RuntimeException
            response.setStatusCode(statusCode).end(errorMessage)
        }
    }

    private fun generateAccessLogBody(it: RoutingContext): JsonObject {
        val now = ZonedDateTime.now()
        val startMs = it.get<Long>("start_ms")
        val nowMs = now.toInstant().toEpochMilli()
        val responseTime = if (startMs == null) 0 else nowMs - startMs
        val headers = it.response().headers()
        val requestHeaders = it.request().headers()

        return json {
            obj(
                "time" to now.toString(),
                "sec" to nowMs,
                "ip" to it.request().remoteAddress().host(),
                "request" to obj(
                    "host" to it.request().host(),
                    "method" to it.request().method().name(),
                    "url" to it.normalizedPath(),
                    "path_params" to it.pathParams(),
                    "query" to it.queryParams().toHashSet().associate { s -> Pair(s.key, s.value) },
                    "headers" to obj(
                        "user_agent" to requestHeaders["User-Agent"],
                        "content_type" to requestHeaders["Content-Type"],
                        "content_length" to requestHeaders["Content-Length"],
                        "accept_language" to requestHeaders["Accept-Language"],
                        "accept" to requestHeaders["Accept"]
                    )
                ),
                "response" to obj(
                    "status" to it.response().statusCode,
                    "headers" to obj(
                        "content_type" to headers["Content-Type"],
                        "content_length" to headers["Content-Length"],
                        "cache_control" to headers["Cache-Control"]
                    )
                ),
                "response_time" to responseTime
            )
        }
    }
}
