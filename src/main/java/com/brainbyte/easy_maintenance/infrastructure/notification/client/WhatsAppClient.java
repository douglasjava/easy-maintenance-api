package com.brainbyte.easy_maintenance.infrastructure.notification.client;

import com.brainbyte.easy_maintenance.commons.exceptions.WhatsAppPermanentException;
import com.brainbyte.easy_maintenance.commons.exceptions.WhatsAppTransientException;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.WhatsAppErrorResponse;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.WhatsAppMessageResponse;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.WhatsAppTemplateMessageRequest;
import com.brainbyte.easy_maintenance.infrastructure.notification.properties.WhatsAppProperties;
import com.brainbyte.easy_maintenance.infrastructure.observability.service.BusinessMetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Cliente da Graph API da Meta (WhatsApp Cloud API), no mesmo estilo do AsaasClient: WebClient
 * próprio (não um bean compartilhado), timeout por chamada, classificação de erro dedicada.
 * Diferente do AsaasClient/MailerSendServiceImpl, o retry aqui é seletivo — só falhas
 * transitórias (ver mapError) — configurado via resilience4j.retry.instances.whatsapp em
 * application.properties (retry-exceptions/ignore-exceptions), não "retry em qualquer exceção".
 */
@Slf4j
@Component
public class WhatsAppClient {

    private static final Duration WHATSAPP_TIMEOUT = Duration.ofSeconds(10);

    // Códigos de erro da Meta (Graph API) — ver
    // https://developers.facebook.com/docs/whatsapp/cloud-api/support/error-codes
    private static final int ERROR_CODE_INVALID_TOKEN = 190;
    private static final int ERROR_CODE_COUNTRY_RESTRICTION = 130497;

    private final WebClient webClient;
    private final WhatsAppProperties properties;
    private final BusinessMetricsService businessMetricsService;
    private final ObjectMapper objectMapper;

    public WhatsAppClient(WhatsAppProperties properties,
                          BusinessMetricsService businessMetricsService,
                          ObjectMapper objectMapper) {

        this.properties = properties;
        this.businessMetricsService = businessMetricsService;
        this.objectMapper = objectMapper;

        this.webClient = WebClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequest())
                .build();

    }

    @Retry(name = "whatsapp", fallbackMethod = "sendTemplateMessageFallback")
    public String sendTemplateMessage(String toE164Phone, String templateName, List<String> templateParams) {
        WhatsAppTemplateMessageRequest request = buildRequest(toE164Phone, templateName, templateParams);

        try {
            WhatsAppMessageResponse response = webClient.post()
                    .uri("/{phoneNumberId}/messages", properties.phoneNumberId())
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::mapError)
                    .bodyToMono(WhatsAppMessageResponse.class)
                    .timeout(WHATSAPP_TIMEOUT)
                    .block();

            String wamid = Optional.ofNullable(response)
                    .map(WhatsAppMessageResponse::messages)
                    .filter(messages -> !messages.isEmpty())
                    .map(List::getFirst)
                    .map(WhatsAppMessageResponse.MessageId::id)
                    .orElse(null);

            businessMetricsService.counter("whatsapp.sent");

            return wamid;

        } catch (WhatsAppTransientException | WhatsAppPermanentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[WhatsApp] Erro de conexão/timeout ao enviar mensagem: {}", e.getMessage());
            throw new WhatsAppTransientException(
                    "Falha transitória (conexão/timeout) ao enviar WhatsApp: " + e.getMessage(), e);
        }

    }

    public String sendTemplateMessageFallback(String toE164Phone, String templateName,
                                               List<String> templateParams, Exception ex) {

        businessMetricsService.counter("whatsapp.failed");

        if (ex instanceof WhatsAppPermanentException permanentException) {
            log.warn("[WhatsApp] Falha permanente ao enviar mensagem: to={} template={} — {}",
                    toE164Phone, templateName, ex.getMessage());
            throw permanentException;
        }

        log.error("[WhatsApp] Falha transitória esgotou todas as tentativas: to={} template={} — {}",
                toE164Phone, templateName, ex.getMessage());
        throw new WhatsAppTransientException(
                "WhatsApp não pôde ser entregue após todas as tentativas: " + ex.getMessage(), ex);

    }

    private Mono<? extends Throwable> mapError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    HttpStatusCode status = response.statusCode();
                    Integer errorCode = extractErrorCode(body);

                    if (status.value() == 401 || (errorCode != null && errorCode == ERROR_CODE_INVALID_TOKEN)) {
                        log.error("WHATSAPP_TOKEN_EXPIRED — Meta rejeitou o token de acesso. status={} body={}",
                                status, body);
                        return new WhatsAppPermanentException(
                                "Token de acesso do WhatsApp inválido/expirado (WHATSAPP_TOKEN_EXPIRED)");
                    }

                    if (errorCode != null && errorCode == ERROR_CODE_COUNTRY_RESTRICTION) {
                        log.warn("[WhatsApp] Erro permanente {} (restrição de país) — status={} body={}",
                                ERROR_CODE_COUNTRY_RESTRICTION, status, body);
                        return new WhatsAppPermanentException(
                                "Mensagem não pôde ser enviada por restrição de país do destinatário (erro "
                                        + ERROR_CODE_COUNTRY_RESTRICTION + ")");
                    }

                    // 429 (rate limit) é a exceção explícita à regra "4xx = permanente" — a Meta libera
                    // a cota depois de um tempo, então vale a pena retentar com backoff.
                    if (status.is5xxServerError() || status.value() == 429) {
                        log.warn("[WhatsApp] Falha transitória — status={} body={}", status, body);
                        return new WhatsAppTransientException(
                                "Falha transitória ao enviar WhatsApp: HTTP " + status + " - " + body);
                    }

                    log.warn("[WhatsApp] Falha permanente — status={} body={}", status, body);
                    return new WhatsAppPermanentException(
                            "Falha permanente ao enviar WhatsApp: HTTP " + status + " - " + body);
                });

    }

    private Integer extractErrorCode(String body) {
        try {
            WhatsAppErrorResponse error = objectMapper.readValue(body, WhatsAppErrorResponse.class);
            return error.error() != null ? error.error().code() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private WhatsAppTemplateMessageRequest buildRequest(String toE164Phone, String templateName,
                                                          List<String> templateParams) {

        // A Graph API espera o número sem o prefixo "+" (ex.: "5531972139145").
        String toDigitsOnly = toE164Phone.startsWith("+") ? toE164Phone.substring(1) : toE164Phone;

        List<WhatsAppTemplateMessageRequest.Parameter> parameters = templateParams.stream()
                .map(value -> new WhatsAppTemplateMessageRequest.Parameter("text", value))
                .toList();

        return WhatsAppTemplateMessageRequest.builder()
                .messagingProduct("whatsapp")
                .to(toDigitsOnly)
                .type("template")
                .template(WhatsAppTemplateMessageRequest.Template.builder()
                        .name(templateName)
                        .language(new WhatsAppTemplateMessageRequest.Language("pt_BR"))
                        .components(List.of(WhatsAppTemplateMessageRequest.Component.builder()
                                .type("body")
                                .parameters(parameters)
                                .build()))
                        .build())
                .build();

    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.info("[WhatsApp] {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }

}
