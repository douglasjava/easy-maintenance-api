package com.brainbyte.easy_maintenance.config;

import com.brainbyte.easy_maintenance.infrastructure.mail.MailService;
import com.brainbyte.easy_maintenance.infrastructure.mail.MailerSendServiceImpl;
import com.brainbyte.easy_maintenance.infrastructure.mail.ResendServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Decide qual {@link MailService} é o ativo de fato (usado por
 * {@code EmailNotificationProvider}/{@code CriticalEmailDispatchService}/{@code EmailRetryJob})
 * via a property {@code mail.provider} — "resend" (padrão) ou "mailersend". As duas
 * implementações concretas continuam registradas como beans sempre (ver
 * {@link ResendServiceImpl}/{@link MailerSendServiceImpl}), só a escolha de qual é a
 * {@code @Primary} muda; isso permite ao {@code JobController#testEmailSend} testar qualquer um
 * dos dois via parâmetro, independente de qual está ativo para o tráfego real.
 */
@Configuration
@Profile("!local")
public class MailProviderConfig {

    @Bean
    @Primary
    public MailService activeMailService(@Value("${mail.provider:resend}") String mailProvider,
                                          ResendServiceImpl resendServiceImpl,
                                          MailerSendServiceImpl mailerSendServiceImpl) {

        return "mailersend".equalsIgnoreCase(mailProvider) ? mailerSendServiceImpl : resendServiceImpl;
    }

}
