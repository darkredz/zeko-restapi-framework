package io.zeko.restapi.core

import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.Vertx


class CircuitBreakerBuilder {
    companion object {
        @JvmStatic
        fun make(vertx: Vertx, name: String, options: CircuitBreakerOptions? = null, retryPolicy: ((Int) -> Long)? = null): CircuitBreaker {
            var opt = options
            if (options == null) {
                opt = CircuitBreakerOptions().apply {
                    maxFailures = 5
                    maxRetries = 8
                    notificationAddress = "vertx.circuit-breaker"
                }
            }
            var policy = retryPolicy
            if (policy == null) {
                policy = { retryCount ->
                    retryCount * 2000L
                }
            }
            return CircuitBreaker.create(name, vertx, opt).retryPolicy(policy)
        }

        @JvmStatic
        fun makeWithUnlimitedRetries(vertx: Vertx, name: String, delayMs: Long = 5000L, maxFailCount: Int = 10): CircuitBreaker {
            val options = CircuitBreakerOptions().apply {
                maxFailures = maxFailCount
                maxRetries = 0
                notificationAddress = "vertx.circuit-breaker"
            }
            val policy = { retryCount: Int -> delayMs }
            return make(vertx, name, options, policy)
        }
    }
}
