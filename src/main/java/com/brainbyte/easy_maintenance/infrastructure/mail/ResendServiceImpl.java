package com.brainbyte.easy_maintenance.infrastructure.mail;

import com.brainbyte.easy_maintenance.commons.exceptions.InternalErrorException;
import com.brainbyte.easy_maintenance.commons.properties.ResendProperties;
import com.brainbyte.easy_maintenance.infrastructure.mail.dto.ResendEmailRequest;
import com.brainbyte.easy_maintenance.infrastructure.mail.dto.ResendEmailResponse;
import com.brainbyte.easy_maintenance.infrastructure.observability.service.BusinessMetricsService;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Provedor de e-mail padrão (plano grátis Resend: 3000/mês, 100/dia) — ver decisão em
 * roadmap/kanban.md. Sempre registrado como bean (junto com {@link MailerSendServiceImpl}) para
 * que o endpoint de teste manual (JobController#testEmailSend) consiga testar os dois provedores
 * independente de qual está ativo; qual dos dois é o {@code MailService} usado de fato pelo resto
 * da aplicação é decidido em {@code MailProviderConfig} via a property {@code mail.provider}.
 * Mesmo padrão de retry-em-qualquer-exceção da MailerSend (sem classificação
 * transitória/permanente como o WhatsAppClient) — troca 1:1 de provedor, não redesenho.
 */
@Slf4j
@Service
@Profile("!local")
public class ResendServiceImpl implements MailService {

    private final WebClient resendWebClient;
    private final ResendProperties resendProperties;
    private final BusinessMetricsService businessMetricsService;

    public ResendServiceImpl(@Qualifier("resendWebClient") WebClient resendWebClient,
                              ResendProperties resendProperties,
                              BusinessMetricsService businessMetricsService) {
        this.resendWebClient = resendWebClient;
        this.resendProperties = resendProperties;
        this.businessMetricsService = businessMetricsService;
    }

    @Retry(name = "resend", fallbackMethod = "sendEmailFallback")
    public void sendEmail(String toEmail, String toName, String subject, String text, String html) {

        ResendEmailRequest payload = ResendEmailRequest.builder()
                .from(String.format("%s <%s>", resendProperties.getFromName(), resendProperties.getFromEmail()))
                .to(List.of(toEmail))
                .subject(subject)
                .text(text)
                .html(html)
                .build();

        ResendEmailResponse response = resendWebClient.post()
                .uri("/emails")
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> {
                                    if (body.contains("daily_quota_exceeded") || body.contains("monthly_quota_exceeded")) {
                                        log.warn("[Resend] Cota do plano grátis atingida — status={} body={}", resp.statusCode(), body);
                                    } else {
                                        log.warn("[Resend] Erro ao enviar e-mail — status={} body={}", resp.statusCode(), body);
                                    }
                                    return Mono.error(new InternalErrorException(
                                            String.format("Resend email send failed: HTTP %s - %s", resp.statusCode(), body)));
                                })
                )
                .bodyToMono(ResendEmailResponse.class)
                .block();

        businessMetricsService.counter("email.sent");
        log.info("[Resend] E-mail enviado: id={} recipient={} subject='{}'",
                response != null ? response.id() : null, toEmail, subject);
    }

    public void sendEmailFallback(String toEmail, String toName, String subject, String text, String html, Exception ex) {
        log.error("[Resend] Falha ao enviar e-mail após todas as tentativas: to={} subject='{}' — {}",
                toEmail, subject, ex.getMessage());
        businessMetricsService.counter("email.failed");
        throw new InternalErrorException("E-mail não pôde ser entregue após todas as tentativas: " + ex.getMessage(), ex);
    }
}
