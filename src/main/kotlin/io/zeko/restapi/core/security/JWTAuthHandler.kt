package io.zeko.restapi.core.security

import io.vertx.core.Handler
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.auth.jwt.JWTAuth
import io.zeko.restapi.core.security.JWTAuthHelper
import io.zeko.restapi.core.utilities.endJson

open class JWTAuthHandler(
        protected val jwtAuth: JWTAuth,
        protected val skipAuth: List<String>,
        protected val continueAfterFail: Boolean = false,
        protected val statusFail: Int = 401
) : Handler<RoutingContext> {

    override fun handle(ctx: RoutingContext) {
        if (this.skipAuth != null && this.skipAuth.contains(ctx.normalisedPath())) {
            ctx.next()
        } else {
            var authHeader = ctx.request().getHeader(HttpHeaders.AUTHORIZATION.toString())
            val helper = JWTAuthHelper(jwtAuth, null)

            helper.validateToken(authHeader) { user, result ->
                if (user == null) {
                    if (continueAfterFail) {
                        ctx.put("tokenStatus", result)
                        ctx.next()
                    } else {
                        ctx.endJson(result, statusFail)
                    }
                } else {
                    ctx.setUser(user)
                    ctx.next()
                }
            }
        }
    }
}
