package io.zeko.restapi.core.mail

import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.Vertx
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.Logger
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.kotlin.ext.web.client.sendJsonAwait
import io.zeko.restapi.core.CircuitBreakerBuilder
import io.zeko.restapi.core.utilities.executeSuspendAwait
import kotlinx.coroutines.*
import java.lang.Exception
import java.util.concurrent.TimeUnit

class SendGridMail(
    val webClient: WebClient,
    val config: MailConfig,
    val logger: Logger,
    val sendEndpoint: String = "/v3/mail/send"
) : MailService {

    companion object {
        @JvmStatic
        fun createSharedClient(vertx: Vertx, options: WebClientOptions? = null): WebClient {
            if (options != null)
                return WebClient.create(vertx, options)

            return WebClient.create(
                vertx, WebClientOptions()
                    .setMaxPoolSize(15)
                    .setDefaultHost("api.sendgrid.com")
                    .setSsl(true)
                    .setDefaultPort(443)
                    .setIdleTimeout(15000)
                    .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)
                    .setConnectTimeout(30000)
                    .setLogActivity(false)
            )
        }

        @JvmStatic
        fun createCircuitBreaker(
            vertx: Vertx,
            name: String = "zeko.mail.sendgrid",
            options: CircuitBreakerOptions? = null
        ): CircuitBreaker {
            return CircuitBreakerBuilder.make(vertx, name, options)
        }
    }

    override suspend fun send(
        toEmail: String,
        name: String,
        subject: String,
        html: String,
        text: String?,
        tags: List<String>?
    ): MailResponse {
        return send(listOf(toEmail), listOf(name), subject, html, text, tags)
    }

    override suspend fun send(
        toList: List<String>,
        subject: String,
        html: String,
        text: String?,
        tags: List<String>?
    ): MailResponse {
        val toEmail = arrayListOf<String>()
        val names = arrayListOf<String>()
        toList.forEach {
            val parts = it.split("<")
            if (parts.size == 2) {
                val name = parts[0]
                val email = it.removePrefix(name).trim().removePrefix("<").removeSuffix(">")
                toEmail.add(email)
                names.add(name)
            } else {
                toEmail.add(it)
                names.add("")
            }
        }
        return send(toEmail, names, subject, html, text, tags)
    }

    override suspend fun send(
        toEmail: List<String>,
        names: List<String>,
        subject: String,
        html: String,
        text: String?,
        tags: List<String>?
    ): MailResponse {
        val content = arrayListOf<Map<String, String>>()

        if (!text.isNullOrEmpty()) {
            content.add(
                mapOf(
                    "type" to "text/plain",
                    "value" to text
                )
            )
        }

        content.add(
            mapOf(
                "type" to "text/html",
                "value" to html
            )
        )

        val toList = arrayListOf<Map<String, String?>>()

        if (config.devMode && !config.toDev.isNullOrEmpty()) {
            toEmail.forEachIndexed { idx, s ->
                toList.add(mapOf("email" to config.toDev, "name" to names[idx]))
            }
        } else {
            toEmail.forEachIndexed { idx, s ->
                toList.add(mapOf("email" to s, "name" to names[idx]))
            }
        }

        val postData = mapOf(
            "personalizations" to listOf(
                mapOf("to" to toList, "subject" to subject)
            ),
            "from" to mapOf("email" to config.fromEmail, "name" to config.fromName),
            "content" to content
        )

        val res = webClient.post(sendEndpoint)
            .putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer ${config.apiKey}")
            .sendJsonAwait(JsonObject(postData))
        val status = res.statusCode()
        val tagStr = if (tags.isNullOrEmpty()) "" else tags.joinToString(",")

        if (status in 200..299) {
            val bodyStr = res.bodyAsString()
            if (bodyStr.isNullOrEmpty()) {
                return MailResponse(true, "", JsonObject().put("results", true))
            } else {
                logger.error("SENDGRID_REJECT ${toEmail[0]} $tagStr ${res.bodyAsString()}")
                return MailResponse(false, res.bodyAsString(), JsonObject().put("results", bodyStr))
            }
        } else {
            logger.error("SENDGRID_FAIL_$status ${toEmail[0]} $tagStr ${res.bodyAsString()}")
            return MailResponse(false, res.bodyAsString(), res.bodyAsJsonObject())
        }
    }

    override suspend fun retry(numRetries: Int, delayTry: Long, operation: suspend (MailService) -> MailResponse) {
        var res: MailResponse? = null
        var shouldRetry = false
        var errMsg = ""
        try {
            res = operation.invoke(this)
            if (!res.success) {
                shouldRetry = true
                errMsg = res.body
            }
        } catch (e: Exception) {
            shouldRetry = true
            errMsg = e.message.toString()
        } finally {
            if (numRetries == 0) {
                if (res == null || !res.success) logger.error("SENDGRID_RETRY_ALL_FAIL $errMsg")
            } else if (shouldRetry) {
                if (numRetries > 0) {
                    if (delayTry > 0) {
                        delay(delayTry)
                    }
                    logger.debug("SENDGRID_RETRY_$numRetries $errMsg")
                    retry(numRetries - 1, delayTry, operation)
                }
            }
        }
    }

    override suspend fun sendInCircuit(
        breaker: CircuitBreaker, toEmail: String,
        name: String, subject: String,
        html: String, text: String?, tags: List<String>?
    ): MailResponse {
        return breaker.executeSuspendAwait {
            val res = send(toEmail, name, subject, html, text, tags)
            if (!res.success) {
                throw Exception(res.body)
            }
            res
        }
    }

}
