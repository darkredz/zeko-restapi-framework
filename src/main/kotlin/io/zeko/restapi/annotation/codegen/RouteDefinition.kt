package io.zeko.restapi.annotation.codegen

data class RouteDefinition(
    val className: String,
    val methodName: String,
    val routePath: String,
    val httpMethod: String,
    val coroutine: Boolean,
    val hasRules: Boolean,
    val pack: String,
    val describe: String,
    val produces: String,
    val consumes: String,
    val schemaRef: String
)

