package io.zeko.restapi.core.controllers

import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.zeko.model.Entity
import io.zeko.restapi.core.validations.ValidateResult

interface Controller {

    fun inputRules(): Map<String, Map<String, String>>

    fun inputErrorMessages(): Map<String, String>

    fun outputError(validateResult: ValidateResult, statusCode: Int, errorCode: Int)

    fun end(value: Any?)

    fun endJson(value: Any?, statusCode: Int = 200)

    fun errorJson(vararg errors: Pair<String, List<String>>)

    fun errorJson(vararg errors: Pair<String, String>, code: Int = 422)

    fun errorJson(errors: Map<String, List<String>>, code: Int = 422)

    fun errorJson(errors: Map<String, List<String>>, statusCode: Int = 422, errorCode: Int = 422)

}
