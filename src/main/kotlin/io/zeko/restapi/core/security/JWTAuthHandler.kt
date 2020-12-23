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
    protected val statusFail: Int = 401,
    protected val useCamelCase: Boolean = false
) : Handler<RoutingContext> {

    override fun handle(ctx: RoutingContext) {
        var skip = false
        if (this.skipAuth != null) {
            val path = ctx.normalizedPath()

            if (this.skipAuth.contains(path)) {
                skip = true
                ctx.next()
            } else {
                val matchList = skipAuth.filter { it.indexOf("*") > 1 }

                for (urlToMatch in matchList) {
                    val parts = urlToMatch.split("*")
                    if (parts.size == 2) {
                        if (
                            parts[1].isNullOrEmpty() && path.indexOf(parts[0]) === 0 ||
                            (!parts[1].isNullOrEmpty() && path.indexOf(parts[0]) === 0 && path.indexOf(parts[1]) === path.length - parts[1].length)
                        ) {
                            skip = true
                            ctx.next()
                            break
                        }
                    }
                }
            }
        }

        if (!skip) {
            var authHeader = ctx.request().getHeader(HttpHeaders.AUTHORIZATION.toString())
            val helper = JWTAuthHelper(jwtAuth, null, useCamelCase)

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
