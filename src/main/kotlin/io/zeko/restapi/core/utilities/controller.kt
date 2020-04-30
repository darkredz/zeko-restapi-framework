package io.zeko.restapi.core.utilities

import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.zeko.model.Entity
import io.zeko.restapi.core.utilities.zip.TempFile
import io.zeko.restapi.core.utilities.zip.ZipGenerator

fun RoutingContext.endJson(value: Any?, statusCode: Int = 200) {
    var output = ""
    when (value) {
        is List<*> -> output = JsonArray(value).encode()
        is Map<*, *> -> output = Json.encode(value)
        is Entity -> output = Json.encode(value)
        is String -> output = value
        else -> output = Json.encode(value)
    }
    this.response().setStatusCode(statusCode)
            .putHeader("Content-Type", "application/json")
            .end(output)
}

fun RoutingContext.errorJson(vararg errors: Pair<String, List<String>>) {
    this.errorJson(mapOf(*errors), 422, 422)
}

fun RoutingContext.errorJson(vararg errors: Pair<String, String>, code: Int = 422) {
    val errMap = HashMap<String, List<String>>()
    errors.forEach {
        errMap.put(it.first, listOf(it.second))
    }
    this.errorJson(errMap, code, code)
}

fun RoutingContext.errorJson(errors: Map<String, List<String>>, code: Int = 422) {
    this.errorJson(errors, code, code)
}

fun RoutingContext.errorJson(errors: Map<String, List<String>>, statusCode: Int = 422, errorCode: Int = 422) {
    this.response().setStatusCode(statusCode)
            .putHeader("Content-Type", "application/json")
            .end(json {
                obj(
                        "error_code" to errorCode,
                        "errors" to JsonObject(errors)
                )
            }.encode())
}

fun RoutingContext.downloadZip(vertx: Vertx, zipName: String, files: List<TempFile>) {
    ZipGenerator.downloadZip(vertx, this, zipName, files)
}
