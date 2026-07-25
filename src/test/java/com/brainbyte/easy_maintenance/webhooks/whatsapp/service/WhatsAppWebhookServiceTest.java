package com.brainbyte.easy_maintenance.webhooks.whatsapp.service;

import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessWhatsAppDispatch;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.BusinessWhatsAppDispatchStatus;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.WhatsAppDeliveryStatus;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessWhatsAppDispatchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookServiceTest {

    private static final String WAMID = "wamid.HBgLNTUzMTk5OTk5OTk5FQIAERgSQUY3ODU5QUY2RUUwOEUxOTQA";

    @Mock BusinessWhatsAppDispatchRepository dispatchRepository;

    WhatsAppWebhookService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppWebhookService(dispatchRepository, new ObjectMapper());
    }

    private BusinessWhatsAppDispatch existingDispatch(WhatsAppDeliveryStatus currentDeliveryStatus) {
        return BusinessWhatsAppDispatch.builder()
                .id(1L)
                .organizationCode("org-1")
                .wamid(WAMID)
                .status(BusinessWhatsAppDispatchStatus.SENT)
                .deliveryStatus(currentDeliveryStatus)
                .build();
    }

    private String statusPayload(String status, String errorsJson) {
        return """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "id": "WABA_ID",
                      "changes": [
                        {
                          "field": "messages",
                          "value": {
                            "messaging_product": "whatsapp",
                            "statuses": [
                              {
                                "id": "%s",
                                "status": "%s",
                                "timestamp": "1690000000",
                                "recipient_id": "5531999999999"%s
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(WAMID, status, errorsJson);
    }

    @Test
    void processEvent_setsDeliveredAt_whenStatusIsDelivered() {
        BusinessWhatsAppDispatch dispatch = existingDispatch(WhatsAppDeliveryStatus.SENT);
        when(dispatchRepository.findByWamid(WAMID)).thenReturn(Optional.of(dispatch));

        service.processEvent(statusPayload("delivered", ""));

        assertThat(dispatch.getDeliveryStatus()).isEqualTo(WhatsAppDeliveryStatus.DELIVERED);
        assertThat(dispatch.getDeliveredAt()).isNotNull();
        verify(dispatchRepository).save(dispatch);
    }

    @Test
    void processEvent_setsReadAt_whenStatusIsRead() {
        BusinessWhatsAppDispatch dispatch = existingDispatch(WhatsAppDeliveryStatus.DELIVERED);
        when(dispatchRepository.findByWamid(WAMID)).thenReturn(Optional.of(dispatch));

        service.processEvent(statusPayload("read", ""));

        assertThat(dispatch.getDeliveryStatus()).isEqualTo(WhatsAppDeliveryStatus.READ);
        assertThat(dispatch.getReadAt()).isNotNull();
        verify(dispatchRepository).save(dispatch);
    }

    @Test
    void processEvent_persistsErrorCodeAndMessage_whenStatusIsFailedWith130497() {
        BusinessWhatsAppDispatch dispatch = existingDispatch(WhatsAppDeliveryStatus.SENT);
        when(dispatchRepository.findByWamid(WAMID)).thenReturn(Optional.of(dispatch));
        String errorsJson = """
                ,
                "errors": [
                  {
                    "code": 130497,
                    "title": "Limite de mensagens business-initiated excedido para este número.",
                    "message": "Message failed to send because there were more than 24 hours since the recipient last replied."
                  }
                ]""";

        service.processEvent(statusPayload("failed", errorsJson));

        assertThat(dispatch.getDeliveryStatus()).isEqualTo(WhatsAppDeliveryStatus.FAILED);
        assertThat(dispatch.getFailedErrorCode()).isEqualTo("130497");
        assertThat(dispatch.getFailedErrorMessage())
                .contains("more than 24 hours since the recipient last replied");
        verify(dispatchRepository).save(dispatch);
    }

    @Test
    void processEvent_doesNotRegressStatus_whenOutOfOrderDeliveredArrivesAfterRead() {
        BusinessWhatsAppDispatch dispatch = existingDispatch(WhatsAppDeliveryStatus.READ);
        when(dispatchRepository.findByWamid(WAMID)).thenReturn(Optional.of(dispatch));

        service.processEvent(statusPayload("delivered", ""));

        assertThat(dispatch.getDeliveryStatus()).isEqualTo(WhatsAppDeliveryStatus.READ);
        verify(dispatchRepository, never()).save(any());
    }

    @Test
    void processEvent_isIdempotent_whenSameStatusEventArrivesTwice() {
        BusinessWhatsAppDispatch dispatch = existingDispatch(WhatsAppDeliveryStatus.DELIVERED);
        when(dispatchRepository.findByWamid(WAMID)).thenReturn(Optional.of(dispatch));

        service.processEvent(statusPayload("delivered", ""));

        assertThat(dispatch.getDeliveryStatus()).isEqualTo(WhatsAppDeliveryStatus.DELIVERED);
        verify(dispatchRepository, never()).save(any());
    }

    @Test
    void processEvent_ignoresEvent_whenWamidNotFound() {
        when(dispatchRepository.findByWamid(WAMID)).thenReturn(Optional.empty());

        service.processEvent(statusPayload("delivered", ""));

        verify(dispatchRepository, never()).save(any());
    }

    @Test
    void processEvent_doesNothing_whenPayloadIsMalformed() {
        service.processEvent("not-json");

        verifyNoInteractions(dispatchRepository);
    }
}
