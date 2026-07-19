package com.brainbyte.easy_maintenance.infrastructure.notification.provider;

import com.brainbyte.easy_maintenance.infrastructure.notification.client.WhatsAppClient;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationPayload;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.WhatsAppSendResult;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationType;
import com.brainbyte.easy_maintenance.infrastructure.notification.properties.WhatsAppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppNotificationProviderTest {

    @Mock WhatsAppClient whatsAppClient;

    private WhatsAppNotificationProvider provider() {
        WhatsAppProperties properties = new WhatsAppProperties(
                "http://localhost", "token", "phone-id", "waba-id", "vencimento_manutencao");
        return new WhatsAppNotificationProvider(whatsAppClient, properties);
    }

    @Test
    void sendTemplateMessage_usesDefaultTemplateWhenPayloadOmitsIt() {
        when(whatsAppClient.sendTemplateMessage(anyString(), anyString(), anyList())).thenReturn("wamid.123");

        NotificationPayload payload = NotificationPayload.builder()
                .recipient("+5531972139145")
                .recipientName("João")
                .templateData(Map.of("itemName", "Extintor", "dueDate", "20/07/2026"))
                .build();

        WhatsAppSendResult result = provider().sendTemplateMessage(payload);

        assertThat(result.wamid()).isEqualTo("wamid.123");
        verify(whatsAppClient).sendTemplateMessage("+5531972139145", "vencimento_manutencao",
                List.of("João", "Extintor", "20/07/2026"));
    }

    @Test
    void sendTemplateMessage_usesTemplateNameFromPayloadWhenProvided() {
        when(whatsAppClient.sendTemplateMessage(anyString(), anyString(), anyList())).thenReturn("wamid.456");

        NotificationPayload payload = NotificationPayload.builder()
                .recipient("+5531972139145")
                .recipientName("Maria")
                .templateName("outro_template")
                .templateData(Map.of("itemName", "Bomba", "dueDate", "01/08/2026"))
                .build();

        provider().sendTemplateMessage(payload);

        ArgumentCaptor<String> templateNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(whatsAppClient).sendTemplateMessage(anyString(), templateNameCaptor.capture(), anyList());
        assertThat(templateNameCaptor.getValue()).isEqualTo("outro_template");
    }

    @Test
    void sendTemplateMessage_fallsBackToRecipientNameWhenTemplateDataOmitsIt() {
        when(whatsAppClient.sendTemplateMessage(anyString(), anyString(), anyList())).thenReturn("wamid.789");

        NotificationPayload payload = NotificationPayload.builder()
                .recipient("+5531972139145")
                .recipientName("Carlos")
                .templateData(Map.of("itemName", "Extintor", "dueDate", "20/07/2026"))
                .build();

        provider().sendTemplateMessage(payload);

        verify(whatsAppClient).sendTemplateMessage("+5531972139145", "vencimento_manutencao",
                List.of("Carlos", "Extintor", "20/07/2026"));
    }

    @Test
    void send_delegatesToSendTemplateMessage() {
        when(whatsAppClient.sendTemplateMessage(anyString(), anyString(), anyList())).thenReturn("wamid.999");

        NotificationPayload payload = NotificationPayload.builder()
                .recipient("+5531972139145")
                .recipientName("Ana")
                .templateData(Map.of())
                .build();

        provider().send(payload);

        verify(whatsAppClient).sendTemplateMessage(anyString(), anyString(), anyList());
    }

    @Test
    void getType_returnsWhatsapp() {
        assertThat(provider().getType()).isEqualTo(NotificationType.WHATSAPP);
    }
}
