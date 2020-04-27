package io.zeko.restapi.core.mail

data class MailConfig(
        val apiKey: String,
        val fromEmail: String,
        val fromName: String,
        var devMode: Boolean = false,
        var toDev: String = ""
)
