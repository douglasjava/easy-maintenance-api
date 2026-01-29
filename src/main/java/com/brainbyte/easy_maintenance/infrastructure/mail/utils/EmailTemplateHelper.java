package com.brainbyte.easy_maintenance.infrastructure.mail.utils;

import org.springframework.stereotype.Component;

@Component
public class EmailTemplateHelper {

    public String generatePasswordResetHtml(String userName, String resetLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { width: 80%%; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 5px; }
                    .header { font-size: 24px; font-weight: bold; margin-bottom: 20px; color: #0056b3; }
                    .button { display: inline-block; padding: 10px 20px; font-size: 16px; color: #fff; background-color: #007bff; text-decoration: none; border-radius: 5px; }
                    .footer { margin-top: 30px; font-size: 12px; color: #777; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">Recuperação de Senha</div>
                    <p>Olá, %s,</p>
                    <p>Recebemos uma solicitação para redefinir sua senha no Easy Maintenance.</p>
                    <p>Clique no botão abaixo para criar uma nova senha. Este link expira em 30 minutos.</p>
                    <p>
                        <a href="%s" class="button">Redefinir Minha Senha</a>
                    </p>
                    <p>Se você não solicitou a recuperação de senha, ignore este e-mail.</p>
                    <div class="footer">
                        Este é um e-mail automático, por favor não responda.
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName, resetLink);
    }
}
