package com.brainbyte.easy_maintenance.infrastructure.notification.provider;

import com.brainbyte.easy_maintenance.infrastructure.notification.client.WhatsAppClient;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationPayload;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.WhatsAppSendResult;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationType;
import com.brainbyte.easy_maintenance.infrastructure.notification.properties.WhatsAppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.util.StringUtils.hasLength;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppNotificationProvider implements NotificationProvider {

    @Value("${notification.whatsapp.support-phone:(31) 99982-6634}")
    private String supportPhone;

    private final WhatsAppClient whatsAppClient;
    private final WhatsAppProperties whatsAppProperties;

    @Override
    public void send(NotificationPayload payload) {
        sendTemplateMessage(payload);
    }

    /**
     * Envia o template e retorna o wamid — usado diretamente por quem precisa do id da mensagem
     * (ex.: TASK-130, para persistir o dispatch antes do callback do webhook da TASK-128).
     * {@link #send(NotificationPayload)} existe só para cumprir a interface {@link NotificationProvider}.
     */
    public WhatsAppSendResult sendTemplateMessage(NotificationPayload payload) {
        String templateName = hasLength(payload.getTemplateName())
                ? payload.getTemplateName()
                : whatsAppProperties.defaultTemplateName();

        List<String> bodyParams = extractBodyParams(payload);
        String buttonParam = extractButtonParam(payload);

        String wamid = whatsAppClient.sendTemplateMessage(payload.getRecipient(), templateName, bodyParams, buttonParam);
        log.info("[WhatsApp] Mensagem enviada: wamid={} recipient={} template={}",
                wamid, payload.getRecipient(), templateName);

        return new WhatsAppSendResult(wamid);

    }


    private List<String> extractBodyParams(NotificationPayload payload) {
        Map<String, Object> data = payload.getTemplateData() != null ? payload.getTemplateData() : Map.of();

        String recipientName = data.containsKey("recipientName")
                ? String.valueOf(data.get("recipientName"))
                : String.valueOf(payload.getRecipientName());

        String itemName = String.valueOf(data.getOrDefault("itemName", ""));
        String companyName = String.valueOf(data.getOrDefault("companyName", ""));
        String dueDate = Optional.ofNullable(data.get("dueDate"))
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .map(LocalDate::parse)
                .map(date -> date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .orElse("");


        return List.of(recipientName, itemName, companyName, dueDate, supportPhone);
    }

    private String extractButtonParam(NotificationPayload payload) {
        Map<String, Object> data = payload.getTemplateData() != null ? payload.getTemplateData() : Map.of();
        return String.valueOf(data.getOrDefault("itemId", ""));
    }

    @Override
    public NotificationType getType() {
        return NotificationType.WHATSAPP;
    }

}
