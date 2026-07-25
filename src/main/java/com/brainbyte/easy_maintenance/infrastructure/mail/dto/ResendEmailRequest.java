package com.brainbyte.easy_maintenance.infrastructure.mail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Corpo da requisição da Resend API (POST /emails). Ao contrário da MailerSend, o remetente é uma
 * única string "Nome &lt;email&gt;" e os destinatários são uma lista de e-mails simples (sem nome) —
 * ver https://resend.com/docs/api-reference/emails/send-email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResendEmailRequest {

    private String from;
    private List<String> to;
    private String subject;
    private String text;
    private String html;

}
