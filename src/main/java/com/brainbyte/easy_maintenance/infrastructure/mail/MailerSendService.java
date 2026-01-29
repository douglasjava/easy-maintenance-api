package com.brainbyte.easy_maintenance.infrastructure.mail;

import com.brainbyte.easy_maintenance.commons.exceptions.InternalErrorException;
import com.brainbyte.easy_maintenance.commons.properties.MailerSendProperties;
import com.brainbyte.easy_maintenance.infrastructure.mail.dto.MailerSendEmailRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
public class MailerSendService {

    private final WebClient mailerSendWebClient;
    private final MailerSendProperties mailerSendProperties;

    public MailerSendService(@Qualifier("mailerSendWebClient") WebClient mailerSendWebClient, MailerSendProperties mailerSendProperties) {
        this.mailerSendWebClient = mailerSendWebClient;
        this.mailerSendProperties = mailerSendProperties;
    }

    public void sendEmail(String toEmail, String toName, String subject, String text, String html) {

        MailerSendEmailRequest payload = MailerSendEmailRequest.builder()
                .from(MailerSendEmailRequest.Person.builder()
                        .email(mailerSendProperties.getFromEmail())
                        .name(mailerSendProperties.getFromName())
                        .build())
                .to(List.of(MailerSendEmailRequest.Person.builder()
                        .email(toEmail)
                        .name(toName)
                        .build()))
                .subject(subject)
                .text(text)
                .html(html)
                .build();

        mailerSendWebClient.post()
                .uri("/email")
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> {
                                    log.warn("MailerSend error status={} body={}", resp.statusCode(), body);
                                    return Mono.error(new InternalErrorException(String.format("MailerSend email send failed: HTTP %s - error %s", resp.statusCode(), body)));
                                })
                )
                .toBodilessEntity().subscribe();

    }
}
