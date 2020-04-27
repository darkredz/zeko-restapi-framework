package io.zeko.restapi.core.mail

import io.vertx.core.json.JsonObject

data class MailResponse(
        val success: Boolean,
        val body: String,
        val jsonBody: JsonObject
)
