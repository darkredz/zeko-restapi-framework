package io.zeko.restapi.core.security

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.User
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.jwt.JWTOptions
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

open class JWTAuthHelper(val jwtAuth: JWTAuth, val jwtAuthRefresh: JWTAuth?) {
    val expireMsg = json {
        obj(
            "auth" to false,
            "error" to obj(
                "token_status" to "expired"
            )
        )
    }

    val invalidMsg = json {
        obj(
            "auth" to false,
            "error" to obj(
                "token_status" to "invalid"
            )
        )
    }

    fun refreshToken(
        refreshToken: String,
        accessToken: String,
        tokenExpireSeconds: Int = 259200,
        refreshExpireSeconds: Int = 604800,
        refreshAfterExpired: Boolean = false,
        authHandler: (User?, JsonObject) -> Unit
    ) {

        jwtAuth.authenticate(json { obj("jwt" to accessToken) }) {
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

            jwtAuthRefresh?.authenticate(json { obj("jwt" to refreshToken) }) {
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
                    } else if (user.containsKey("for_token") && user["for_token"] == accessToken) {
                        user.remove("for_token")
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
        val tokenData = json { obj("jwt" to accessToken) }

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
        refreshExpireSeconds: Int = 604800,
        useCamelCase: Boolean = false
    ): Map<String, String> {
        val token = jwtAuth.generateToken(jwtAuthData, JWTOptions().setExpiresInSeconds(tokenExpireSeconds)) + ""

        val refreshData = if (useCamelCase) {
            jwtAuthData.copy().put("forToken", token)
        } else {
            jwtAuthData.copy().put("for_token", token)
        }
        val refreshToken =
            jwtAuthRefresh?.generateToken(refreshData, JWTOptions().setExpiresInSeconds(refreshExpireSeconds)) + ""

        if (useCamelCase) {
            return mapOf(
                "accessToken" to token,
                "refreshToken" to refreshToken
            )
        }
        return mapOf(
            "access_token" to token,
            "refresh_token" to refreshToken
        )
    }

    companion object {
        @JvmStatic
        fun createJWTAuth(vertx: Vertx, jwtOptions: JWTAuthOptions) = JWTAuth.create(vertx, jwtOptions)
    }
}
