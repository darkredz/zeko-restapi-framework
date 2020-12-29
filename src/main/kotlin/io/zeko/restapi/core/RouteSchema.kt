package io.zeko.restapi.core

import io.vertx.core.Vertx
import org.slf4j.Logger
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlin.coroutines.CoroutineContext

abstract class RouteSchema(val vertx: Vertx) {
    open fun handleRoutes(
        router: Router,
        logger: Logger,
        koto: (route: Route, fn: suspend (rc: RoutingContext, cc: CoroutineContext) -> Unit) -> Unit
    ) {
    }
}
