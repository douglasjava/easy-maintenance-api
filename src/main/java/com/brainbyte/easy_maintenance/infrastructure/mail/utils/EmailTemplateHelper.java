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

    public String generateSubscriptionExpirationHtml(String userName, String paymentLink, String expirationDate) {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                .container { width: 80%%; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 5px; }
                .header { font-size: 24px; font-weight: bold; margin-bottom: 20px; color: #d9534f; }
                .button { display: inline-block; padding: 12px 22px; font-size: 16px; color: #fff; background-color: #28a745; text-decoration: none; border-radius: 5px; }
                .warning { background-color: #fff3cd; padding: 10px; border-radius: 5px; margin: 15px 0; }
                .footer { margin-top: 30px; font-size: 12px; color: #777; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">Aviso de Vencimento de Assinatura</div>
               \s
                <p>Olá, %s,</p>
               \s
                <p>Identificamos que sua assinatura do <strong>Easy Maintenance</strong> está próxima do vencimento.</p>
               \s
                <div class="warning">
                    <strong>Data de vencimento:</strong> %s
                </div>
               \s
                <p>Para evitar qualquer interrupção no acesso ao sistema, recomendamos que realize o pagamento antes da data informada.</p>
               \s
                <p>
                    <a href="%s" class="button">Renovar Assinatura</a>
                </p>
               \s
                <p>Se você já realizou o pagamento, por favor desconsidere esta mensagem.</p>
               \s
                <div class="footer">
                    Este é um e-mail automático, por favor não responda.<br>
                    © %d Easy Maintenance. Todos os direitos reservados.
                </div>
            </div>
        </body>
        </html>
       \s""".formatted(userName, expirationDate, paymentLink, java.time.Year.now().getValue());
    }

}
