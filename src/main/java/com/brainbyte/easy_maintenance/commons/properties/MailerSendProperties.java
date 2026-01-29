package com.brainbyte.easy_maintenance.commons.properties;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties("mailersend")
public class MailerSendProperties {

    private String apiKey;
    private String baseUrl;
    private String fromEmail;
    private String fromName;

}
