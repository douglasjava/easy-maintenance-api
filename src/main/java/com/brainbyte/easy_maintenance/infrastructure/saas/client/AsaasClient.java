package com.brainbyte.easy_maintenance.infrastructure.saas.client;

import com.brainbyte.easy_maintenance.commons.exceptions.AsaasException;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.properties.AsaasProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
public class AsaasClient {

    private final WebClient webClient;

    public AsaasClient(AsaasProperties props) {
        this.webClient = WebClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("access_token", props.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    private static final Duration ASAAS_TIMEOUT = Duration.ofSeconds(10);

    @CircuitBreaker(name = "asaas")
    public AsaasDTO.CustomerResponse createCustomer(AsaasDTO.CreateCustomerRequest req) {
        return webClient.post()
                .uri("/customers")
                .bodyValue(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(AsaasDTO.CustomerResponse.class)
                .timeout(ASAAS_TIMEOUT)
                .block();
    }

    @CircuitBreaker(name = "asaas")
    public AsaasDTO.SubscriptionResponse createSubscription(AsaasDTO.CreateSubscriptionRequest req) {
        return webClient.post()
                .uri("/subscriptions")
                .bodyValue(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(AsaasDTO.SubscriptionResponse.class)
                .timeout(ASAAS_TIMEOUT)
                .block();
    }

    @CircuitBreaker(name = "asaas")
    public AsaasDTO.SubscriptionResponse getSubscription(String subscriptionId) {
        return webClient.get()
                .uri("/subscriptions/{id}", subscriptionId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(AsaasDTO.SubscriptionResponse.class)
                .timeout(ASAAS_TIMEOUT)
                .block();
    }

    @CircuitBreaker(name = "asaas")
    public AsaasDTO.SubscriptionResponse updateSubscription(String subscriptionId, AsaasDTO.UpdateSubscriptionRequest req) {
        return webClient.post()
                .uri("/subscriptions/{id}", subscriptionId)
                .bodyValue(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(AsaasDTO.SubscriptionResponse.class)
                .timeout(ASAAS_TIMEOUT)
                .block();
    }

    @CircuitBreaker(name = "asaas")
    public void cancelSubscription(String subscriptionId) {
        webClient.delete()
                .uri("/subscriptions/{id}", subscriptionId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .toBodilessEntity()
                .timeout(ASAAS_TIMEOUT)
                .block();
    }

    @CircuitBreaker(name = "asaas")
    public AsaasDTO.PaymentResponse createPayment(AsaasDTO.CreatePaymentRequest req) {
        return webClient.post()
                .uri("/payments")
                .bodyValue(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(AsaasDTO.PaymentResponse.class)
                .timeout(ASAAS_TIMEOUT)
                .block();
    }

    @CircuitBreaker(name = "asaas")
    public AsaasDTO.CheckoutResponse createCheckout(AsaasDTO.CreateCheckoutRequest req) {
        return webClient.post()
                .uri("/checkouts")
                .bodyValue(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(AsaasDTO.CheckoutResponse.class)
                .timeout(ASAAS_TIMEOUT)
                .block();
    }

    private Mono<? extends Throwable> mapError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    String msg = "Asaas API error: status=" + response.statusCode() + " body=" + body;
                    log.error(msg);
                    return new AsaasException(msg);
                });
    }

    private static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(req -> {
            log.debug("Asaas Request: {} {}", req.method(), req.url());
            return Mono.just(req);
        });
    }

    private static ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(res -> {
            log.debug("Asaas Response status: {}", res.statusCode());
            return Mono.just(res);
        });
    }

}

