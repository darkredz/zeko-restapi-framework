package io.zeko.restapi.core

import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext

class TimeoutHandler(
    private val timeout: Long,
    private val errorCode: Int,
    private val skipPaths: List<String>? = null
) : Handler<RoutingContext> {
    override fun handle(ctx: RoutingContext) {
        var shouldSkip = false

        if (skipPaths != null) {
            val path = ctx.normalisedPath()
            for (uri in skipPaths) {
                if (uri.contains("*")) {
                    if (path.startsWith(uri.removeSuffix("*"))) {
                        shouldSkip = true
                        break
                    }
                } else {
                    if (path == uri) {
                        shouldSkip = true
                        break
                    }
                }
            }
        }

        if (shouldSkip) {
            ctx.next()
        } else {
            val tid = ctx.vertx().setTimer(timeout) { ctx.fail(errorCode) }
            ctx.addBodyEndHandler { ctx.vertx().cancelTimer(tid) }
            ctx.next()
        }
    }
}
