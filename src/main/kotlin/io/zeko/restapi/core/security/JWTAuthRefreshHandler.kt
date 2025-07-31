package io.zeko.restapi.core.security

import io.vertx.core.Handler
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.auth.jwt.JWTAuth
import io.zeko.restapi.core.utilities.endJson

open class JWTAuthRefreshHandler(
    protected val jwtAuth: JWTAuth,
    protected val jwtAuthRefresh: JWTAuth,
    protected val tokenExpireSeconds: Int = 259200,
    protected val refreshExpireSeconds: Int = 604800,
    protected val refreshAfterExpired: Boolean = false,
    protected val useCamelCase: Boolean = false
) : Handler<RoutingContext> {

    override fun handle(ctx: RoutingContext) {
        var accessToken: String = ""
        val authHeader = ctx.request().getHeader(HttpHeaders.AUTHORIZATION.toString())
        if (!authHeader.isNullOrEmpty()) {
            accessToken = authHeader.removePrefix("Bearer ")
        } else {
            accessToken = "invalid"
        }

        val refreshTokenKey = if (useCamelCase) "refreshToken" else "refresh_token"
        var refreshToken = ctx.request().params().get(refreshTokenKey) + ""
        val helper = JWTAuthHelper(jwtAuth, jwtAuthRefresh, useCamelCase)

        helper.refreshToken(
            refreshToken, accessToken, tokenExpireSeconds,
            refreshExpireSeconds, refreshAfterExpired
        ) { user, result ->
            if (user == null) {
                ctx.response().statusCode = 401
                ctx.endJson(result)
            } else {
                ctx.endJson(result)
            }
        }
    }

}
