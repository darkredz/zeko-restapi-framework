package io.zeko.restapi.annotation.codegen

class RoutingAnnotation (map: Map<String, Any?>) {
    protected val defaultMap = map.withDefault { null }
    val path: String? by defaultMap
    val method: String? by defaultMap
    val coroutine: Boolean? by defaultMap
    val describe: String? by defaultMap
    val produces: String? by defaultMap
    val consumes: String? by defaultMap
    val schemaRef: String? by defaultMap
}
