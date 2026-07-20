package com.brainbyte.easy_maintenance.config;

import com.brainbyte.easy_maintenance.infrastructure.notification.properties.WhatsAppProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(WhatsAppProperties.class)
public class WhatsAppConfig {

    // TASK-131: injetado (em vez de Clock.systemDefaultZone() direto) só para permitir controlar
    // o "agora" em testes da checagem de horário comercial (BusinessWhatsAppNotificationService).
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
