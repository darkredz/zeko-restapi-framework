package io.zeko.restapi.core.utilities

import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.kotlin.circuitbreaker.executeAwait
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

fun makeCircuitBreaker(vertx: Vertx, name: String, options: CircuitBreakerOptions? = null): CircuitBreaker {
    var opt = options
    if (options == null) {
        opt = CircuitBreakerOptions().apply {
            maxFailures = 4
            maxRetries = 6
            notificationAddress = "vertx.circuit-breaker"
        }
    }
    return CircuitBreaker.create(name, vertx, opt).retryPolicy { retryCount ->
        retryCount * 2000L
    }
}

suspend fun <T> CircuitBreaker.executeSuspendAwait(block: suspend (Promise<T>) -> T): T {
    val circuitBreaker = this
    return coroutineScope {
        val scope = this
        circuitBreaker.executeAwait<T> { promise ->
            scope.launch {
                runCatching {
                    block(promise)
                }.onFailure {
                    promise.fail(it)
                }.onSuccess {
                    promise.complete(it)
                }
            }
        }
    }
}
