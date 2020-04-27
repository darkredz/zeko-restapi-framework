package io.zeko.restapi.annotation.http

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Get(
        val path: String,
        val describe: String = "",
        val schemaRef: String = "",
        val produces: String = "",
        val consumes: String = ""
)
