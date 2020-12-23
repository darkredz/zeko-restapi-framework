package io.zeko.restapi.core.mail

import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.Logger
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.kotlin.coroutines.await
import io.zeko.restapi.core.CircuitBreakerBuilder
import io.zeko.restapi.core.utilities.executeSuspendAwait
import kotlinx.coroutines.*
import java.lang.Exception
import java.util.concurrent.TimeUnit

class MandrillMail(
    val webClient: WebClient,
    val config: MailConfig,
    val logger: Logger,
    val sendEndpoint: String = "/api/1.0/messages/send.json"
) : MailService {

    companion object {
        @JvmStatic
        fun createSharedClient(vertx: Vertx, options: WebClientOptions? = null): WebClient {
            if (options != null)
                return WebClient.create(vertx, options)

            return WebClient.create(
                vertx, WebClientOptions()
                    .setMaxPoolSize(15)
                    .setDefaultHost("mandrillapp.com")
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
            name: String = "zeko.mail.mandrill",
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
        val postData = mapOf(
            "key" to config.apiKey,
            "message" to mutableMapOf(
                "html" to html,
                "subject" to subject,
                "from_email" to config.fromEmail,
                "from_name" to config.fromName
            )
        )

        val msg = postData["message"] as MutableMap<String, Any>

        if (!tags.isNullOrEmpty()) {
            msg["tags"] = tags
        }

        if (text.isNullOrEmpty()) {
            msg["auto_text"] = true
        } else {
            msg["text"] = text
        }

        val toList = arrayListOf<Map<String, String?>>()

        if (config.devMode && !config.toDev.isNullOrEmpty()) {
            toEmail.forEachIndexed { idx, s ->
                toList.add(mapOf("type" to "to", "email" to config.toDev, "name" to names[idx]))
            }
        } else {
            toEmail.forEachIndexed { idx, s ->
                toList.add(mapOf("type" to "to", "email" to s, "name" to names[idx]))
            }
        }

        msg["to"] = toList

        val res = webClient.post(sendEndpoint).sendJson(JsonObject(postData)).await()
        val status = res.statusCode()
        val tagStr = if (tags.isNullOrEmpty()) "" else tags.joinToString(",")

        if (status in 200..299) {
            val json = res.bodyAsJsonArray()
            if (json != null && !json.isEmpty && json.size() > 0) {
                val mandrillStatus = json.getJsonObject(0).getString("status")
                if (mandrillStatus == "sent") {
                    return MailResponse(true, res.bodyAsString(), JsonObject().put("results", json))
                } else {
                    logger.error("MANDRILL_REJECT ${toEmail[0]} $tagStr $json")
                    return MailResponse(false, res.bodyAsString(), JsonObject().put("results", json))
                }
            } else {
                //mandrill failed
                logger.error("MANDRILL_FAIL_$status ${toEmail[0]} $tagStr $json")
                return MailResponse(false, res.bodyAsString(), JsonObject().put("results", json))
            }
        } else {
            logger.error("MANDRILL_FAIL_$status ${toEmail[0]} $tagStr ${res.bodyAsString()}")
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
                if (res == null || !res.success) logger.error("MANDRILL_RETRY_ALL_FAIL $errMsg")
            } else if (shouldRetry) {
                if (numRetries > 0) {
                    if (delayTry > 0) {
                        delay(delayTry)
                    }
                    logger.debug("MANDRILL_RETRY_$numRetries $errMsg")
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
