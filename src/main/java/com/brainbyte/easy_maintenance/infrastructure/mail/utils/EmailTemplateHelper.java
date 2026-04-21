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

    public String generateAdminInvitationHtml(String userName, String email, String password, String loginLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { width: 80%%; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 5px; }
                    .header { font-size: 24px; font-weight: bold; margin-bottom: 20px; color: #0056b3; }
                    .credentials { background-color: #f8f9fa; padding: 15px; border-left: 4px solid #007bff; margin: 20px 0; }
                    .button { display: inline-block; padding: 10px 20px; font-size: 16px; color: #fff; background-color: #007bff; text-decoration: none; border-radius: 5px; }
                    .footer { margin-top: 30px; font-size: 12px; color: #777; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">Conclua seu cadastro na Easy Maintenance</div>
                    <p>Olá, %s!</p>
                    <p>Seu acesso à plataforma Easy Maintenance foi criado com sucesso pelo administrador do sistema.</p>
                    
                    <p>Utilize as credenciais abaixo para seu primeiro acesso:</p>
                    <div class="credentials">
                        <strong>Login (E-mail):</strong> %s<br>
                        <strong>Senha:</strong> %s
                    </div>
                    
                    <p>Para começar a utilizar a plataforma, é necessário concluir seu cadastro e definir as informações pendentes de acesso.</p>
                    <p>Acesse o link enviado pelo sistema ou utilize o fluxo de primeiro acesso para finalizar seu cadastro.</p>
                    <p>
                        <a href="%s" class="button">Acessar Plataforma</a>
                    </p>
                    <p>Se você não esperava este convite, desconsidere este e-mail ou entre em contato com o administrador responsável.</p>
                    <div class="footer">
                        Atenciosamente,<br>
                        Equipe Easy Maintenance
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName, email, password, loginLink);
    }

    public String generateTrialActivatedHtml(String userName, String dataFimTrial, String linkSistema) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { width: 80%%; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 5px; }
                    .header { font-size: 24px; font-weight: bold; margin-bottom: 20px; color: #0056b3; }
                    .button { display: inline-block; padding: 12px 22px; font-size: 16px; color: #fff; background-color: #007bff; text-decoration: none; border-radius: 5px; }
                    .footer { margin-top: 30px; font-size: 12px; color: #777; }
                    ul { padding-left: 20px; }
                    li { margin-bottom: 5px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">Seu acesso completo ao Easy Maintenance foi liberado</div>
                    <p>Olá, %s!</p>
                    
                    <p>Seu cadastro inicial foi concluído com sucesso e seu acesso ao Easy Maintenance já está liberado.</p>
                    
                    <p>A partir de agora, você pode utilizar a plataforma com acesso completo em período TRIAL por 7 dias, até <strong>%s</strong>.</p>
                    
                    <p>Durante esse período, você poderá explorar os principais recursos do sistema, como:</p>
                    <ul>
                        <li>cadastro e gestão de empresas</li>
                        <li>organização dos itens de manutenção</li>
                        <li>acompanhamento das manutenções e vencimentos</li>
                        <li>visão inicial da operação em um só lugar</li>
                    </ul>
                    
                    <p>Para acessar sua conta, clique no link abaixo:</p>
                    <p>
                        <a href="%s" class="button">Acessar Minha Conta</a>
                    </p>
                    
                    <p>Aproveite esse período para configurar sua operação e conhecer melhor a plataforma.</p>
                    
                    <p>Se precisar de apoio, estaremos à disposição.</p>
                    
                    <div class="footer">
                        Atenciosamente,<br>
                        Equipe Easy Maintenance
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName, dataFimTrial, linkSistema);
    }

    public String generateCancellationProcessedHtml(String userName, String organizationName) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { width: 80%%; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 5px; }
                    .header { font-size: 24px; font-weight: bold; margin-bottom: 20px; color: #333; }
                    .footer { margin-top: 30px; font-size: 12px; color: #777; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">Cancelamento de Assinatura</div>
                    <p>Olá, %s,</p>
                    <p>Informamos que o cancelamento da assinatura da organização <strong>%s</strong> foi processado com sucesso.</p>
                    <p>Conforme solicitado, seu acesso e as funcionalidades vinculadas à assinatura foram encerrados.</p>
                    <p>Agradecemos o período em que estivemos juntos. Caso tenha qualquer dúvida ou deseje reativar seu acesso no futuro, nossa equipe está à disposição.</p>
                    <div class="footer">
                        Atenciosamente,<br>
                        Equipe Easy Maintenance
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName, organizationName);
    }

    public String generateSubscriptionBlockedHtml(String userName, String organizationName) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { width: 80%%; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 5px; }
                    .header { font-size: 24px; font-weight: bold; margin-bottom: 20px; color: #d9534f; }
                    .footer { margin-top: 30px; font-size: 12px; color: #777; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">Assinatura Bloqueada</div>
                    <p>Olá, %s,</p>
                    <p>Identificamos uma pendência em sua conta e, por esse motivo, o acesso à assinatura da organização <strong>%s</strong> foi temporariamente bloqueado.</p>
                    <p>Esta ação ocorre devido a pendências de cobrança ou regras de faturamento não concluídas. Para restabelecer seu acesso, solicitamos que regularize a situação financeira na plataforma.</p>
                    <p>Se tiver dúvidas sobre como proceder para a regularização ou acreditar que houve um erro, entre em contato conosco.</p>
                    <div class="footer">
                        Atenciosamente,<br>
                        Equipe Easy Maintenance
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName, organizationName);
    }

    public String generatePixOverdueHtml(String userName, String amountFormatted, String paymentLink) {
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
                    <div class="header">Pagamento PIX em Atraso</div>
                    <p>Olá, %s,</p>
                    <p>Identificamos que seu pagamento via PIX referente à assinatura do <strong>Easy Maintenance</strong> está em atraso.</p>
                    <div class="warning">
                        <strong>Valor pendente:</strong> %s
                    </div>
                    <p>Para evitar o bloqueio do seu acesso, realize o pagamento o quanto antes usando o link abaixo.</p>
                    <p>
                        <a href="%s" class="button">Pagar com PIX</a>
                    </p>
                    <p>Se você já realizou o pagamento, por favor desconsidere esta mensagem.</p>
                    <div class="footer">
                        Este é um e-mail automático, por favor não responda.<br>
                        © %d Easy Maintenance. Todos os direitos reservados.
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName, amountFormatted, paymentLink, java.time.Year.now().getValue());
    }

    public static String generateNotificationEventHtml(String userName, String title, String description) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { width: 80%%; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 5px; }
                    .header { font-size: 24px; font-weight: bold; margin-bottom: 20px; color: #0056b3; }
                    .footer { margin-top: 30px; font-size: 12px; color: #777; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">%s</div>
                    <p>Olá, %s,</p>
                    <p>%s</p>
                    <p>Acesse o sistema para conferir os detalhes e tomar as ações necessárias.</p>
                    <div class="footer">
                        Atenciosamente,<br>
                        Equipe Easy Maintenance
                    </div>
                </div>
            </body>
            </html>
            """.formatted(title, userName != null ? userName : "Usuário", description);
    }

}
