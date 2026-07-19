package com.brainbyte.easy_maintenance.config;

import com.brainbyte.easy_maintenance.infrastructure.notification.properties.WhatsAppProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WhatsAppProperties.class)
public class WhatsAppConfig {
}
