package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationTemplateBuilder {

    public String buildHtml(String templateName, Map<String, Object> data) {
        return switch (templateName) {
            case "WELCOME" -> buildWelcomeHtml(data);
            case "MAINTENANCE_ALERT" -> buildMaintenanceAlertHtml(data);
            default -> data.getOrDefault("content", "").toString();
        };
    }

    public String buildText(String templateName, Map<String, Object> data) {
        return switch (templateName) {
            case "WELCOME" -> "Bem-vindo ao Easy Maintenance, " + data.get("userName");
            case "MAINTENANCE_ALERT" -> "Alerta de Manutenção: O item " + data.get("itemName") + " está próximo do vencimento.";
            default -> data.getOrDefault("content", "").toString();
        };
    }

    private String buildWelcomeHtml(Map<String, Object> data) {
        return """
            <html>
                <body>
                    <h1>Bem-vindo, %s!</h1>
                    <p>Sua conta no Easy Maintenance foi criada com sucesso.</p>
                </body>
            </html>
            """.formatted(data.get("userName"));
    }

    private String buildMaintenanceAlertHtml(Map<String, Object> data) {
        return """
            <html>
                <body>
                    <h1>Alerta de Manutenção</h1>
                    <p>O item <strong>%s</strong> está com manutenção agendada para <strong>%s</strong>.</p>
                </body>
            </html>
            """.formatted(data.get("itemName"), data.get("dueDate"));
    }
}
