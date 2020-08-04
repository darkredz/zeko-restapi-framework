package io.zeko.restapi.core.security

import io.vertx.core.Vertx
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions

class JWTHelper {
    companion object {
        @JvmStatic
        fun createJWTAuth(vertx: Vertx, jwtOptions: JWTAuthOptions) = JWTAuth.create(vertx, jwtOptions)
    }
}
