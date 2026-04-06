package com.brainbyte.easy_maintenance.infrastructure.mail;

import com.brainbyte.easy_maintenance.commons.properties.MailerSendProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!local")
@RequiredArgsConstructor
public class MailHogServiceImpl implements MailService {

    private final JavaMailSender mailSender;
    private final MailerSendProperties mailerSendProperties;

    @Override
    public void sendEmail(String toEmail, String toName, String subject, String text, String html) {
        log.info("Sending email via MailHog (Simple) to {} ({}) - Subject: {}", toName, toEmail, subject);
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(String.format("%s <%s>", mailerSendProperties.getFromName(), mailerSendProperties.getFromEmail()));
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(text);
            
            mailSender.send(message);
            log.info("Email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {} via MailHog: {}. Certifique-se que o MailHog está rodando em localhost:1025", toEmail, e.getMessage());
        }
    }
}
