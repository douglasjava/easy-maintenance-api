package com.brainbyte.easy_maintenance.infrastructure.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.service.account}")
    private String firebaseConfigJson;

    @PostConstruct
    public void initialize() {
        try {
            if (firebaseConfigJson == null || firebaseConfigJson.isBlank()) {
                log.warn("FIREBASE_SERVICE_ACCOUNT_JSON não configurada. Notificações push não funcionarão.");
                return;
            }

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(firebaseConfigJson.getBytes(StandardCharsets.UTF_8))))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase inicializado com sucesso.");
            }

        } catch (IOException e) {
            log.error("Erro ao inicializar Firebase: {}", e.getMessage());
        }

    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        if (FirebaseApp.getApps().isEmpty()) {
            return null;
        }
        return FirebaseMessaging.getInstance();
    }
}
