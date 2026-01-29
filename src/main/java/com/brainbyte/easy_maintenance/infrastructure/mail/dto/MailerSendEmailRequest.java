package com.brainbyte.easy_maintenance.infrastructure.mail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MailerSendEmailRequest {

    private Person from;
    private List<Person> to;
    private String subject;

    private String text;
    private String html;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Person {
        private String email;
        private String name;
    }

}
