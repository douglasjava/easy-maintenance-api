package com.brainbyte.easy_maintenance.webhooks.whatsapp.controller;

import com.brainbyte.easy_maintenance.infrastructure.notification.properties.WhatsAppProperties;
import com.brainbyte.easy_maintenance.webhooks.whatsapp.security.WhatsAppSignatureValidator;
import com.brainbyte.easy_maintenance.webhooks.whatsapp.service.WhatsAppWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {

    @Mock WhatsAppWebhookService webhookService;
    @Mock WhatsAppSignatureValidator signatureValidator;
    @Mock HttpServletRequest request;

    WhatsAppWebhookController controller;

    @BeforeEach
    void setUp() {
        WhatsAppProperties properties = new WhatsAppProperties(
                "https://graph.facebook.com/v21.0", "token", "phoneId", "wabaId",
                "template", "expected-verify-token", "app-secret");
        controller = new WhatsAppWebhookController(webhookService, signatureValidator, properties);
    }

    @Test
    void verify_returnsChallengeWith200_whenTokenMatchesAndModeIsSubscribe() {
        var response = controller.verify("subscribe", "expected-verify-token", "challenge-123", request);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isEqualTo("challenge-123");
    }

    @Test
    void verify_returns403_whenTokenDoesNotMatch() {
        var response = controller.verify("subscribe", "wrong-token", "challenge-123", request);

        assertThat(response.getStatusCode()).isEqualTo(FORBIDDEN);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void verify_returns403_whenModeIsNotSubscribe() {
        var response = controller.verify("unsubscribe", "expected-verify-token", "challenge-123", request);

        assertThat(response.getStatusCode()).isEqualTo(FORBIDDEN);
    }

    @Test
    void verify_returns403_whenParamsMissing() {
        var response = controller.verify(null, null, null, request);

        assertThat(response.getStatusCode()).isEqualTo(FORBIDDEN);
    }

    @Test
    void receive_returns403_andDoesNotProcess_whenSignatureInvalid() {
        when(signatureValidator.isValid(anyString(), any())).thenReturn(false);

        var response = controller.receive("{}", "sha256=forged", request);

        assertThat(response.getStatusCode()).isEqualTo(FORBIDDEN);
        verify(webhookService, never()).processEvent(anyString());
    }

    @Test
    void receive_returns200_andProcessesAsync_whenSignatureValid() {
        when(signatureValidator.isValid("{}", "sha256=valid")).thenReturn(true);

        var response = controller.receive("{}", "sha256=valid", request);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        verify(webhookService).processEvent("{}");
    }
}
