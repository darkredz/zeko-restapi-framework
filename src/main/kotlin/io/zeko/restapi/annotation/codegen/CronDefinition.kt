package io.zeko.restapi.annotation.codegen

data class CronDefinition (
    val className: String,
    val methodName: String,
    val schedule: String,
    val coroutine: Boolean,
    val pack: String
)

