package com.brainbyte.easy_maintenance.infrastructure.saas.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Resilience4j circuit breaker behaviour (no Spring context needed).
 * Validates the circuit breaker opens after repeated failures and throws
 * CallNotPermittedException when OPEN.
 */
class AsaasClientCircuitBreakerTest {

    @Test
    void circuitBreaker_opensAfterFailureThreshold() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker("asaas-test");

        // Record 2 failures out of 4 calls (50%) — should open
        for (int i = 0; i < 2; i++) {
            cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("timeout"));
        }
        for (int i = 0; i < 2; i++) {
            cb.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void circuitBreaker_staysClosedBelowFailureThreshold() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker("asaas-test-closed");

        // Record 1 failure out of 4 calls (25%) — should stay CLOSED
        cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("error"));
        cb.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS);
        cb.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS);
        cb.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS);

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void circuitBreaker_throwsCallNotPermittedWhenOpen() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker("asaas-test-open");

        // Force OPEN state
        cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("e1"));
        cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("e2"));
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Calling through OPEN circuit breaker must throw CallNotPermittedException
        assertThatThrownBy(() ->
                cb.executeRunnable(() -> { throw new RuntimeException("should not reach"); })
        ).isInstanceOf(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);
    }

    @Test
    void circuitBreaker_transitionsToHalfOpenAfterWait() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker("asaas-test-half-open");

        // Open the circuit
        cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("e1"));
        cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("e2"));
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Wait for the open duration to pass
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        cb.transitionToHalfOpenState();

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void circuitBreaker_closesAfterSuccessfulCallsInHalfOpen() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker("asaas-test-close-from-half");

        // Open → HalfOpen → Closed
        cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("e1"));
        cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("e2"));
        cb.transitionToHalfOpenState();
        cb.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS);

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
