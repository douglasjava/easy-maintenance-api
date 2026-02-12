package com.brainbyte.easy_maintenance.infrastructure.notification.provider;

import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationPayload;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationType;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.push.domain.UserPushToken;
import com.brainbyte.easy_maintenance.push.infrastructure.repository.UserPushTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PushNotificationProvider implements NotificationProvider {

    private final FirebaseMessaging firebaseMessaging;
    private final UserPushTokenRepository tokenRepository;
    private final UserRepository userRepository;

    @Override
    public void send(NotificationPayload payload) {
        if (firebaseMessaging == null) {
            log.warn("FirebaseMessaging não disponível. Notificação ignorada para {}", payload.getRecipient());
            return;
        }

        userRepository.findById(payload.getIdUser()).ifPresent(user -> {
            List<UserPushToken> tokens = tokenRepository.findAllByUserAndActiveIsTrue(user);
            tokens.forEach(token -> sendToToken(token.getToken(), payload));
        });

    }

    private void sendToToken(String token, NotificationPayload payload) {
        try {

            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(payload.getSubject())
                            .setBody(payload.getContent())
                            .build())
                    .build();

            String response = firebaseMessaging.send(message);
            log.info("Notificação PUSH enviada com sucesso para {}. ID da resposta: {}", token, response);

        } catch (Exception e) {
            log.error("Erro ao enviar notificação PUSH para {}: {}", token, e.getMessage());

            if (e.getMessage().contains("registration-token-not-registered") || e.getMessage().contains("invalid-registration-token")) {
                tokenRepository.findByToken(token).ifPresent(t -> {
                    t.setActive(false);
                    tokenRepository.save(t);
                    log.info("Token {} desativado por ser inválido.", token);
                });
            }

        }
    }

    @Override
    public NotificationType getType() {
        return NotificationType.PUSH;
    }

}
