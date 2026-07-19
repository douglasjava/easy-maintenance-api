package com.brainbyte.easy_maintenance.infrastructure.notification.provider;

import com.brainbyte.easy_maintenance.infrastructure.notification.client.WhatsAppClient;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationPayload;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.WhatsAppSendResult;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationType;
import com.brainbyte.easy_maintenance.infrastructure.notification.properties.WhatsAppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.springframework.util.StringUtils.hasLength;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppNotificationProvider implements NotificationProvider {

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

        List<String> params = extractTemplateParams(payload);

        String wamid = whatsAppClient.sendTemplateMessage(payload.getRecipient(), templateName, params);
        log.info("[WhatsApp] Mensagem enviada: wamid={} recipient={} template={}",
                wamid, payload.getRecipient(), templateName);

        return new WhatsAppSendResult(wamid);

    }

    // Ordem fixa dos parâmetros posicionais do template (nome do destinatário, nome do item, data
    // de vencimento) — a Graph API não valida por nome, só por posição. Confirmar esta ordem
    // contra o template registrado no WhatsApp Manager antes de alterá-la (ver TASK-129, Escopo #1).
    private List<String> extractTemplateParams(NotificationPayload payload) {
        Map<String, Object> data = payload.getTemplateData() != null ? payload.getTemplateData() : Map.of();

        String recipientName = data.containsKey("recipientName")
                ? String.valueOf(data.get("recipientName"))
                : String.valueOf(payload.getRecipientName());

        String itemName = String.valueOf(data.getOrDefault("itemName", ""));
        String dueDate = String.valueOf(data.getOrDefault("dueDate", ""));

        return List.of(recipientName, itemName, dueDate);
    }

    @Override
    public NotificationType getType() {
        return NotificationType.WHATSAPP;
    }

}
