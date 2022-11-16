package io.zeko.restapi.core.security

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.User
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.auth.JWTOptions
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.zeko.db.sql.utilities.toCamelCase

open class JWTAuthHelper(val jwtAuth: JWTAuth, val jwtAuthRefresh: JWTAuth?, val useCamelCase: Boolean = false) {
    val expireMsg: JsonObject
    val invalidMsg: JsonObject

    init {
        expireMsg = json {
            obj(
                "auth" to false,
                "error" to obj(
                    getJsonKey("token_status") to "expired"
                )
            )
        }

        invalidMsg = json {
            obj(
                "auth" to false,
                "error" to obj(
                    getJsonKey("token_status") to "invalid"
                )
            )
        }
    }

    fun refreshToken(
        refreshToken: String,
        accessToken: String,
        tokenExpireSeconds: Int = 259200,
        refreshExpireSeconds: Int = 604800,
        refreshAfterExpired: Boolean = false,
        authHandler: (User?, JsonObject) -> Unit
    ) {

        jwtAuth.authenticate(json { obj("jwt" to accessToken, "token" to accessToken) }) {
            var expired = false

            if (it.failed()) {
                val msg = it.cause().message + ""
                expired = msg.indexOf("Expired") > -1
                if (!expired) {
                    authHandler(null, invalidMsg)
                    return@authenticate
                }
            }

            if (refreshAfterExpired && !expired) {
                authHandler(null, invalidMsg)
                return@authenticate
            }

            jwtAuthRefresh?.authenticate(json { obj("jwt" to refreshToken, "token" to refreshToken) }) {
                if (it.failed()) {
                    val msg = it.cause().message + ""

                    if (msg.indexOf("Expired") > -1) {
                        authHandler(null, expireMsg)
                    } else {
                        authHandler(null, invalidMsg)
                    }
                } else {
                    val authUser = it.result() as User
                    val user = authUser.principal().map

                    if (accessToken.isNullOrEmpty()) {
                        authHandler(null, invalidMsg)
                    } else if (user.containsKey(getJsonKey("for_token")) && user[getJsonKey("for_token")] == accessToken) {
                        user.remove(getJsonKey("for_token"))
                        authHandler(
                            authUser,
                            JsonObject(generateAuthTokens(JsonObject(user), tokenExpireSeconds, refreshExpireSeconds))
                        )
                    } else {
                        authHandler(null, invalidMsg)
                    }
                }
            }
        }
    }

    fun validateToken(authHeader: String?, authHandler: (User?, JsonObject) -> Unit) {
        if (authHeader.isNullOrEmpty()) {
            authHandler(null, invalidMsg)
            return
        }

        var accessToken = authHeader.removePrefix("Bearer ")
        val tokenData = json { obj("jwt" to accessToken, "token" to accessToken) }

        jwtAuth.authenticate(tokenData) {
            if (it.failed()) {
                val msg = it.cause().message + ""

                if (msg.indexOf("Expired") > -1) {
                    authHandler(null, expireMsg)
                } else {
                    authHandler(null, invalidMsg)
                }
            } else {
                val user = it.result() as User
                authHandler(user, user.principal())
            }
        }
    }

    fun generateAuthTokens(
        jwtAuthData: JsonObject,
        tokenExpireSeconds: Int = 259200,
        refreshExpireSeconds: Int = 604800
    ): Map<String, String> {
        val token = jwtAuth.generateToken(jwtAuthData, JWTOptions().setExpiresInSeconds(tokenExpireSeconds)) + ""

        val refreshData = jwtAuthData.copy().put(getJsonKey("for_token"), token)
        val refreshToken =
            jwtAuthRefresh?.generateToken(refreshData, JWTOptions().setExpiresInSeconds(refreshExpireSeconds)) + ""

        return mapOf(
            getJsonKey("access_token") to token,
            getJsonKey("refresh_token") to refreshToken
        )
    }

    protected fun getJsonKey(key: String): String {
        if (useCamelCase)
            return key.toCamelCase()
        return key
    }

    companion object {
        @JvmStatic
        fun createJWTAuth(vertx: Vertx, jwtOptions: JWTAuthOptions) = JWTAuth.create(vertx, jwtOptions)
    }
}
