package io.zeko.restapi.annotation.http

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Routing(
        val path: String,
        val method: String = "",
        val coroutine: Boolean = false,
        val describe: String = "",
        val schemaRef: String = "",
        val produces: String = "",
        val consumes: String = ""
)
