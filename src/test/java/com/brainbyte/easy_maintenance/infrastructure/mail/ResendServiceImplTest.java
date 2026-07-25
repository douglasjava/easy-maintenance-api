package com.brainbyte.easy_maintenance.infrastructure.mail;

import com.brainbyte.easy_maintenance.commons.exceptions.InternalErrorException;
import com.brainbyte.easy_maintenance.commons.properties.ResendProperties;
import com.brainbyte.easy_maintenance.infrastructure.observability.service.BusinessMetricsService;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResendServiceImplTest {

    private HttpServer server;
    private ResendServiceImpl service;
    private SimpleMeterRegistry meterRegistry;
    private volatile String capturedRequestBody;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();

        ResendProperties properties = new ResendProperties();
        properties.setApiKey("re_fake_key");
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setFromEmail("notificacoes@notify.easymaintenance.com.br");
        properties.setFromName("Easy Maintenance");

        WebClient webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        meterRegistry = new SimpleMeterRegistry();
        service = new ResendServiceImpl(webClient, properties, new BusinessMetricsService(meterRegistry));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void stubEmailsEndpoint(int status, String body) {
        server.createContext("/emails", exchange -> {
            capturedRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @Test
    void sendEmail_onSuccess_sendsCorrectPayloadAndIncrementsSentMetric() {
        stubEmailsEndpoint(200, """
                {"id": "re_abc123"}
                """);

        service.sendEmail("cliente@teste.com", "Cliente Teste", "Vencimento próximo",
                "Seu item vence em breve", "<p>Seu item vence em breve</p>");

        assertThat(capturedRequestBody).contains("\"from\":\"Easy Maintenance <notificacoes@notify.easymaintenance.com.br>\"");
        assertThat(capturedRequestBody).contains("\"to\":[\"cliente@teste.com\"]");
        assertThat(capturedRequestBody).contains("\"subject\":\"Vencimento próximo\"");
        assertThat(capturedRequestBody).contains("\"html\":\"<p>Seu item vence em breve</p>\"");
        assertThat(meterRegistry.get("easy_email.sent").counter().count()).isEqualTo(1.0);
    }

    @Test
    void sendEmail_onErrorStatus_throwsInternalErrorException() {
        stubEmailsEndpoint(429, """
                {"statusCode": 429, "name": "daily_quota_exceeded", "message": "You have reached your daily email quota."}
                """);

        assertThatThrownBy(() -> service.sendEmail("cliente@teste.com", "Cliente Teste", "assunto", "texto", "<p>html</p>"))
                .isInstanceOf(InternalErrorException.class)
                .hasMessageContaining("Resend");
    }

    @Test
    void sendEmailFallback_incrementsFailedMetricAndThrowsInternalErrorException() {
        assertThatThrownBy(() -> service.sendEmailFallback("cliente@teste.com", "Cliente Teste",
                "assunto", "texto", "<p>html</p>", new RuntimeException("falha simulada")))
                .isInstanceOf(InternalErrorException.class);

        assertThat(meterRegistry.get("easy_email.failed").counter().count()).isEqualTo(1.0);
    }
}
