package com.brainbyte.easy_maintenance.onboarding.application.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingPlanRepository;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.onboarding.application.dto.OnboardingDTO;
import com.brainbyte.easy_maintenance.onboarding.mapper.IOnboardingMapper;
import com.brainbyte.easy_maintenance.org_users.application.service.OrganizationsService;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.payment.application.factory.PaymentProviderFactory;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.infrastructure.mail.MailService;
import com.brainbyte.easy_maintenance.infrastructure.mail.utils.EmailTemplateHelper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final BillingAccountRepository billingAccountRepository;
    private final PaymentProviderFactory providerFactory;
    private final OrganizationsService organizationsService;
    private final UsersService usersService;
    private final BillingSubscriptionService billingSubscriptionService;
    private final BillingPlanRepository billingPlanRepository;
    private final MailService mailService;
    private final EmailTemplateHelper emailTemplateHelper;

    @Value("${frontend.login-url}")
    private String loginUrl;

    @Transactional
    public OnboardingDTO.AccountUserResponse createUser(User user, OnboardingDTO.AccountUserRequest request) {
        log.info("Creating user account and subscription with id {} ", user.getId());

        log.info("1. Buscar ou criar conta de cobrança");
        var account = billingAccountRepository.findByUserId(user.getId())
                .orElseGet(() -> BillingAccount.builder().user(user).build());

        mergeBillingAccountChanges(request, account);

        log.info("2. Valida e/ou criar usuário gateway de pagamento (ASAAS)");
        if (account.getExternalCustomerId() == null) {
            try {
                var customer = IOnboardingMapper.INSTANCE.toCustomerDTO(account);
                var externalCustomerId = providerFactory.get(PaymentProvider.ASAAS).createExternalCustomer(customer);
                account.setExternalCustomerId(externalCustomerId);
            } catch (Exception e) {
                log.warn("Asaas unavailable during onboarding for userId={}. Continuing without externalCustomerId.",
                        user.getId(), e);
            }
        }

        BillingAccount savedAccount = billingAccountRepository.save(account);

        log.info("3. Buscar ou criar BillingSubscription (Trial de 7 dias padrão)");
        var billingSubscription = billingSubscriptionService.findByUser(user.getId())
                .orElseGet(() -> billingSubscriptionService.createTrial(savedAccount, Duration.ofDays(7)));

        var userPlan = getBillingPlanByCode(request.planCode());

        log.info("5. Adicionar item USER à assinatura");
        billingSubscriptionService.addItem(billingSubscription, BillingSubscriptionItemSourceType.USER, user.getId().toString(), userPlan);

        var billingAccountResponse = IBillingMapper.INSTANCE.toBillingAccountResponse(savedAccount);

        return new OnboardingDTO.AccountUserResponse(
                billingAccountResponse.id(),
                billingSubscription.getId()
        );

    }

    @Transactional
    public OnboardingDTO.AccountOrganizationResponse createOrganization(User user, OnboardingDTO.AccountOrganizationRequest request) {
        log.info("Creating organization {} for user {}", request.code(), user.getId());

        log.info("1. Obter BillingSubscription ativa do usuário");
        var billingSubscription = billingSubscriptionService.findByUser(user.getId())
                .orElseThrow(() -> new NotFoundException("Assinatura ativa não encontrada para usuário: " + user.getId()));

        log.info("2. Criar Entidade Organization de domínio");
        var organizationRequest = IOnboardingMapper.INSTANCE.toCreateOrganizationRequest(request);
        var createdOrganization = organizationsService.create(organizationRequest);

        log.info("Organization criada com id {} ", createdOrganization.id());

        log.info("3. Vincular User X Organization (Permissões de domínio)");
        usersService.addOrganization(user.getId(), createdOrganization.code());

        var orgPlan = getBillingPlanByCode(request.planCode());

        log.info("5. Adicionar item ORGANIZATION à BillingSubscription");
        billingSubscriptionService.addItem(billingSubscription, BillingSubscriptionItemSourceType.ORGANIZATION, createdOrganization.code(), orgPlan);

        log.info("6. Enviar e-mail de ativação do trial");
        sendTrialActivatedEmail(user, billingSubscription);

        return new OnboardingDTO.AccountOrganizationResponse(
                createdOrganization.id(),
                billingSubscription.getId(),
                createdOrganization.code(),
                createdOrganization.name()
        );

    }

    private void sendTrialActivatedEmail(User user, BillingSubscription subscription) {
        try {
            var dataFimTrial = "";
            if (subscription.getCurrentPeriodEnd() != null) {
                dataFimTrial = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        .withZone(ZoneId.systemDefault())
                        .format(subscription.getCurrentPeriodEnd());
            }

            String htmlContent = emailTemplateHelper.generateTrialActivatedHtml(user.getName(), dataFimTrial, loginUrl);
            String subject = "Seu acesso completo ao Easy Maintenance foi liberado";
            String textContent = "Olá, " + user.getName() + "! Seu acesso ao Easy Maintenance foi liberado até " + dataFimTrial + ". Acesse em: " + loginUrl;

            mailService.sendEmail(user.getEmail(), user.getName(), subject, textContent, htmlContent);
            log.info("E-mail de ativação do trial enviado para {}", user.getEmail());
        } catch (Exception e) {
            log.error("Erro ao enviar e-mail de ativação do trial para {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private static void mergeBillingAccountChanges(OnboardingDTO.AccountUserRequest request, BillingAccount account) {

        if (request.name() != null) account.setName(request.name());
        if (request.billingEmail() != null) account.setBillingEmail(request.billingEmail());
        if (request.paymentMethod() != null) account.setPaymentMethod(request.paymentMethod());
        if (request.doc() != null) account.setDoc(request.doc());
        if (request.street() != null) account.setStreet(request.street());
        if (request.number() != null) account.setNumber(request.number());
        if (request.complement() != null) account.setComplement(request.complement());
        if (request.neighborhood() != null) account.setNeighborhood(request.neighborhood());
        if (request.city() != null) account.setCity(request.city());
        if (request.state() != null) account.setState(request.state());
        if (request.zipCode() != null) account.setZipCode(request.zipCode());
        if (request.country() != null) account.setCountry(request.country());
        if (request.status() != null) account.setStatus(request.status());
        if (request.phone() != null) account.setPhone(request.phone());

    }

    private BillingPlan getBillingPlanByCode(String planCode) {
        log.info("4. Buscar plano escolhido {}", planCode);
        return billingPlanRepository.findByCode(planCode)
                .orElseThrow(() -> new NotFoundException("Plano não encontrado: " + planCode));
    }

}
