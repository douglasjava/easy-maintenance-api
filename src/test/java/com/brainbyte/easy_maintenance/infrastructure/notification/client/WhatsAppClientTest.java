package com.brainbyte.easy_maintenance.infrastructure.notification.client;

import com.brainbyte.easy_maintenance.commons.exceptions.WhatsAppPermanentException;
import com.brainbyte.easy_maintenance.commons.exceptions.WhatsAppTransientException;
import com.brainbyte.easy_maintenance.infrastructure.notification.properties.WhatsAppProperties;
import com.brainbyte.easy_maintenance.infrastructure.observability.service.BusinessMetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhatsAppClientTest {

    private HttpServer server;
    private WhatsAppClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();

        WhatsAppProperties properties = new WhatsAppProperties(
                "http://localhost:" + server.getAddress().getPort(),
                "fake-token",
                "123456",
                "789",
                "vencimento_manutencao",
                "verify-token",
                "app-secret"
        );

        client = new WhatsAppClient(properties, new BusinessMetricsService(new SimpleMeterRegistry()), new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void stubMessagesEndpoint(int status, String body) {
        server.createContext("/123456/messages", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @Test
    void sendTemplateMessage_onSuccess_returnsWamid() {
        stubMessagesEndpoint(200, """
                {
                  "messaging_product": "whatsapp",
                  "contacts": [{"input": "+5531972139145", "wa_id": "5531972139145"}],
                  "messages": [{"id": "wamid.HBgLNTUzMTk3MjEzOTE0NQ=="}]
                }
                """);

        String wamid = client.sendTemplateMessage("+5531972139145", "vencimento_manutencao",
                List.of("João", "Extintor", "20/07/2026"));

        assertThat(wamid).isEqualTo("wamid.HBgLNTUzMTk3MjEzOTE0NQ==");
    }

    @Test
    void sendTemplateMessage_on5xx_throwsTransientException() {
        stubMessagesEndpoint(500, """
                {"error": {"message": "Internal error", "type": "OAuthException", "code": 1}}
                """);

        assertThatThrownBy(() -> client.sendTemplateMessage("+5531972139145", "vencimento_manutencao", List.of("a", "b", "c")))
                .isInstanceOf(WhatsAppTransientException.class);
    }

    @Test
    void sendTemplateMessage_on401_throwsPermanentExceptionWithTokenExpiredMarker() {
        stubMessagesEndpoint(401, """
                {"error": {"message": "Invalid OAuth access token", "type": "OAuthException", "code": 190}}
                """);

        assertThatThrownBy(() -> client.sendTemplateMessage("+5531972139145", "vencimento_manutencao", List.of("a", "b", "c")))
                .isInstanceOf(WhatsAppPermanentException.class)
                .hasMessageContaining("WHATSAPP_TOKEN_EXPIRED");
    }

    @Test
    void sendTemplateMessage_onCountryRestrictionError_throwsPermanentException() {
        stubMessagesEndpoint(470, """
                {"error": {"message": "Message failed to send because of a country restriction", "type": "OAuthException", "code": 130497}}
                """);

        assertThatThrownBy(() -> client.sendTemplateMessage("+5531972139145", "vencimento_manutencao", List.of("a", "b", "c")))
                .isInstanceOf(WhatsAppPermanentException.class)
                .hasMessageContaining("130497");
    }

    @Test
    void sendTemplateMessage_onInvalidTemplate4xx_throwsPermanentException() {
        stubMessagesEndpoint(400, """
                {"error": {"message": "Template name does not exist", "type": "OAuthException", "code": 132001}}
                """);

        assertThatThrownBy(() -> client.sendTemplateMessage("+5531972139145", "template_inexistente", List.of("a", "b", "c")))
                .isInstanceOf(WhatsAppPermanentException.class);
    }

    @Test
    void sendTemplateMessage_on429RateLimit_throwsTransientException() {
        stubMessagesEndpoint(429, """
                {"error": {"message": "Too many requests", "type": "OAuthException", "code": 80007}}
                """);

        assertThatThrownBy(() -> client.sendTemplateMessage("+5531972139145", "vencimento_manutencao", List.of("a", "b", "c")))
                .isInstanceOf(WhatsAppTransientException.class);
    }

    @Test
    void sendTemplateMessage_onConnectionFailure_throwsTransientException() {
        server.stop(0);

        assertThatThrownBy(() -> client.sendTemplateMessage("+5531972139145", "vencimento_manutencao", List.of("a", "b", "c")))
                .isInstanceOf(WhatsAppTransientException.class);
    }
}
