package com.brainbyte.easy_maintenance.onboarding.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAccountDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.OrganizationSubscriptionDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.UserSubscriptionDTO;
import com.brainbyte.easy_maintenance.billing.application.service.OrganizationSubscriptionService;
import com.brainbyte.easy_maintenance.billing.application.service.UserSubscriptionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.onboarding.application.dto.OnboardingDTO;
import com.brainbyte.easy_maintenance.onboarding.mapper.IOnboardingMapper;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.application.service.OrganizationsService;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.payment.application.dto.CustomerDTO;
import com.brainbyte.easy_maintenance.payment.application.factory.PaymentProviderFactory;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final BillingAccountRepository billingAccountRepository;
    private final PaymentProviderFactory providerFactory;
    private final UserSubscriptionService userSubscriptionService;
    private final OrganizationsService organizationsService;
    private final OrganizationSubscriptionService organizationSubscriptionService;
    private final UsersService usersService;

    @Transactional
    public OnboardingDTO.AccountUserResponse createUser(User user, OnboardingDTO.AccountUserRequest request) {
        log.info("Creating user with id {} ", user.getId());

        // Buscar conta existente
        var account = billingAccountRepository.findByUserId(user.getId())
                .orElseGet(() -> BillingAccount.builder().user(user).build());

        // Criar subscrição de usuário
        var updateSubscriptionRequest = createUpdateSubscriptionRequest(request, user);
        var userSubscriptionResponse = userSubscriptionService.updateOrCreate(user.getId(), updateSubscriptionRequest);

        // Merge campos da conta
        mergeBillingAccountChanges(request, account);

        // Valida e/ou criar usuário gateway de pagamento (ASAAS)
        if(account.getExternalCustomerId() == null) {

            var customer = IOnboardingMapper.INSTANCE.toCustomerDTO(account);
            var externalCustomerId = providerFactory.get(PaymentProvider.ASAAS).createExternalCustomer(customer);

            account.setExternalCustomerId(externalCustomerId);

        }

        BillingAccount save = billingAccountRepository.save(account);

        var billingAccountResponse = IBillingMapper.INSTANCE.toBillingAccountResponse(save);

        return new OnboardingDTO.AccountUserResponse(
                billingAccountResponse.id(),
                userSubscriptionResponse.userId()
        );

    }

    @Transactional
    public OnboardingDTO.AccountOrganizationResponse createOrganization(User user, OnboardingDTO.AccountOrganizationRequest request) {
        log.info("Creating organization with id {} ", request.code());

        //Criar Organization
        var organization = IOnboardingMapper.INSTANCE.toCreateOrganizationRequest(request);
        var createdOrganization = organizationsService.create(organization);

        log.info("Organization created with id {} ", createdOrganization.id());

        // Criar subscrição de Organization
        var updateSubscriptionRequest = IOnboardingMapper.INSTANCE.toupdateSubscriptionRequest(request, user);
        var organizationSubscription = organizationSubscriptionService.updateOrCreate(createdOrganization.code(), updateSubscriptionRequest);

        log.info("Organization subscription created with id {} ", organizationSubscription.id());

        // Vincular User X Organization
        usersService.addOrganization(user.getId(), createdOrganization.code());

        log.info("UserXOrganization created");

        return new OnboardingDTO.AccountOrganizationResponse(
                createdOrganization.id(),
                organizationSubscription.id(),
                createdOrganization.code(),
                createdOrganization.name()
        );

    }

    private static UserSubscriptionDTO.UpdateSubscriptionRequest createUpdateSubscriptionRequest(OnboardingDTO.AccountUserRequest request, User user) {
        return new UserSubscriptionDTO.UpdateSubscriptionRequest(
                user.getId(),
                request.planCode(),
                request.subscriptionStatus(),
                request.currentPeriodStart(),
                request.currentPeriodEnd(),
                request.trialEndsAt()
        );
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

}
