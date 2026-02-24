package com.brainbyte.easy_maintenance.infrastructure.mail;

public interface MailService {

    void sendEmail(String toEmail, String toName, String subject, String text, String html);

}
