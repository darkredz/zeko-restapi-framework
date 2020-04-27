package io.zeko.restapi.core.mail

import io.vertx.circuitbreaker.CircuitBreaker

interface MailService {
    suspend fun send(
            toEmail: String, name: String,
            subject: String, content: String,
            text: String? = null, tags: List<String>? = null
    ): MailResponse

    suspend fun send(
            toList: List<String>, subject: String,
            content: String, text: String? = null,
            tags: List<String>? = null
    ): MailResponse

    suspend fun send(
            toEmail: List<String>, names: List<String>,
            subject: String, content: String, text: String? = null,
            tags: List<String>? = null
    ): MailResponse

    suspend fun retry(numRetries: Int, delayTry: Long = 0, operation: suspend (MailService) -> MailResponse)

    suspend fun sendInCircuit(
            breaker: CircuitBreaker, toEmail: String,
            name: String, subject: String, content: String,
            text: String? = null, tags: List<String>? = null
    ): MailResponse

}
