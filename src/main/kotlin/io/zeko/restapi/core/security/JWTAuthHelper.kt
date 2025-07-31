package io.zeko.restapi.core.security

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.User
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.authentication.Credentials
import io.vertx.ext.auth.authentication.TokenCredentials
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.coAwait
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

    suspend fun refreshToken(
        refreshToken: String,
        accessToken: String,
        tokenExpireSeconds: Int = 259200,
        refreshExpireSeconds: Int = 604800,
        refreshAfterExpired: Boolean = false,
        authHandler: (User?, JsonObject) -> Unit
    ) {
        try {
            jwtAuth.authenticate(TokenCredentials(accessToken)).coAwait()
        } catch (e: Exception) {
            val msg = e.message + ""
            val expired = msg.toLowerCase().indexOf("expired") > -1
            if (!expired) {
                authHandler(null, invalidMsg)
                return
            }

            if (!refreshAfterExpired || jwtAuthRefresh == null) {
                authHandler(null, expireMsg)
                return
            }

            try {
                val authUser = jwtAuthRefresh.authenticate(TokenCredentials(refreshToken)).coAwait()
                val user = authUser.principal().map

                if (accessToken.isEmpty()) {
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
            } catch (refreshErr: Exception) {
                val refreshErrMeg = e.message + ""
                if (refreshErrMeg.toLowerCase().indexOf("expired") > -1) {
                    authHandler(null, expireMsg)
                } else {
                    authHandler(null, invalidMsg)
                }
                return
            }
        }
    }

    suspend fun validateToken(authHeader: String?, authHandler: (User?, JsonObject) -> Unit) {
        if (authHeader.isNullOrEmpty()) {
            authHandler(null, invalidMsg)
            return
        }

        val accessToken = authHeader.removePrefix("Bearer ")

        try {
            val user = jwtAuth.authenticate(TokenCredentials(accessToken)).coAwait()
            authHandler(user, user.principal())
        } catch (e: Exception) {
            val msg = e.message + ""
            if (msg.indexOf("Expired") > -1) {
                authHandler(null, expireMsg)
            } else {
                authHandler(null, invalidMsg)
            }
            return
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
