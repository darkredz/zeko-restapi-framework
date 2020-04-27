package io.zeko.restapi.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Params(val rules: Array<String>)
