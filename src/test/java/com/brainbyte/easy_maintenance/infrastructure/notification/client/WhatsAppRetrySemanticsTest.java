package com.brainbyte.easy_maintenance.infrastructure.notification.client;

import com.brainbyte.easy_maintenance.commons.exceptions.WhatsAppPermanentException;
import com.brainbyte.easy_maintenance.commons.exceptions.WhatsAppTransientException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o MECANISMO de retry seletivo do Resilience4j isoladamente (mesmo estilo de
 * AsaasClientCircuitBreakerTest para circuit breaker) — reproduz a config de
 * resilience4j.retry.instances.whatsapp (application.properties): retry-exceptions restrito a
 * WhatsAppTransientException, para que WhatsAppPermanentException nunca seja retentada.
 */
class WhatsAppRetrySemanticsTest {

    private Retry buildWhatsAppRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(WhatsAppTransientException.class)
                .build();

        return RetryRegistry.of(config).retry("whatsapp-test");
    }

    @Test
    void retriesTransientFailureUntilSuccess() {
        Retry retry = buildWhatsAppRetry();
        AtomicInteger attempts = new AtomicInteger();

        Supplier<String> supplier = Retry.decorateSupplier(retry, () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new WhatsAppTransientException("falha transitória simulada, tentativa " + attempt);
            }
            return "wamid.ok";
        });

        String result = supplier.get();

        assertThat(result).isEqualTo("wamid.ok");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryPermanentFailure() {
        Retry retry = buildWhatsAppRetry();
        AtomicInteger attempts = new AtomicInteger();

        Supplier<String> supplier = Retry.decorateSupplier(retry, () -> {
            attempts.incrementAndGet();
            throw new WhatsAppPermanentException("falha permanente simulada");
        });

        assertThatThrownBy(supplier::get).isInstanceOf(WhatsAppPermanentException.class);
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void givesUpAfterMaxAttemptsOnPersistentTransientFailure() {
        Retry retry = buildWhatsAppRetry();
        AtomicInteger attempts = new AtomicInteger();

        Supplier<String> supplier = Retry.decorateSupplier(retry, () -> {
            attempts.incrementAndGet();
            throw new WhatsAppTransientException("sempre falha");
        });

        assertThatThrownBy(supplier::get).isInstanceOf(WhatsAppTransientException.class);
        assertThat(attempts.get()).isEqualTo(3);
    }
}
