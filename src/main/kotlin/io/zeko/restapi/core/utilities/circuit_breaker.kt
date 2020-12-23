package io.zeko.restapi.core.utilities

import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.kotlin.circuitbreaker.executeAwait
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun <T> CircuitBreaker.executeSuspendAwait(block: suspend (Promise<T>) -> T): T {
    val circuitBreaker = this
    return coroutineScope {
        val scope = this
        val future = circuitBreaker.execute<T> { promise ->
            scope.launch {
                runCatching {
                    block(promise)
                }.onFailure {
                    promise.fail(it)
                }.onSuccess {
                    promise.complete(it)
                }
            }
        }.await()
        future
    }
}
