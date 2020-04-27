package io.zeko.restapi.annotation.cron

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Cron(
        val schedule: String
)
