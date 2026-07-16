package com.brainbyte.easy_maintenance.onboarding.application.service;

import com.brainbyte.easy_maintenance.ai.application.dto.CompanyType;
import com.brainbyte.easy_maintenance.billing.application.service.BillingNotificationService;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingPlanRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.infrastructure.mail.utils.EmailTemplateHelper;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.CriticalEmailDispatchService;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.onboarding.application.dto.OnboardingDTO;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.application.service.OrganizationsService;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.payment.application.factory.PaymentProviderFactory;
import com.brainbyte.easy_maintenance.payment.application.service.PaymentProviderStrategy;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * EPIC-014 / TASK-110 — onboarding deixa de gerar cobrança duplicada USER+ORGANIZATION.
 * {@link BillingSubscriptionService} é usado como instância real (não mockada) para observar
 * o totalCents resultante da orquestração completa de createUser + N x createOrganization.
 */
@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock BillingAccountRepository billingAccountRepository;
    @Mock PaymentProviderFactory providerFactory;
    @Mock PaymentProviderStrategy paymentProviderStrategy;
    @Mock OrganizationsService organizationsService;
    @Mock UsersService usersService;
    @Mock BillingPlanRepository billingPlanRepository;
    @Mock CriticalEmailDispatchService criticalEmailDispatchService;
    @Mock EmailTemplateHelper emailTemplateHelper;

    @Mock BillingSubscriptionRepository billingSubscriptionRepository;
    @Mock BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock AsaasClient asaasClient;
    @Mock BillingNotificationService billingNotificationService;
    @Mock BillingPlanFeaturesHelper billingPlanFeaturesHelper;
    @Mock com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository maintenanceItemRepository;

    private OnboardingService service;

    private static final Long USER_ID = 42L;
    private final AtomicReference<BillingSubscription> subscriptionHolder = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        BillingSubscriptionService billingSubscriptionService = new BillingSubscriptionService(
                billingSubscriptionRepository, billingSubscriptionItemRepository, asaasClient, billingNotificationService,
                billingPlanFeaturesHelper, maintenanceItemRepository);

        service = new OnboardingService(
                billingAccountRepository, providerFactory, organizationsService, usersService,
                billingSubscriptionService, billingPlanRepository, criticalEmailDispatchService, emailTemplateHelper);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(billingAccountRepository.save(any(BillingAccount.class)))
                .thenAnswer(inv -> {
                    BillingAccount account = inv.getArgument(0);
                    if (account.getId() == null) account.setId(1L);
                    return account;
                });

        when(providerFactory.get(PaymentProvider.ASAAS)).thenReturn(paymentProviderStrategy);
        when(paymentProviderStrategy.createExternalCustomer(any())).thenReturn("cus_test_123");

        when(billingPlanRepository.findByCode("BUSINESS"))
                .thenReturn(Optional.of(BillingPlan.builder().code("BUSINESS").name("Business").priceCents(29900).build()));

        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenAnswer(inv -> Optional.ofNullable(subscriptionHolder.get()));

        when(billingSubscriptionRepository.save(any(BillingSubscription.class)))
                .thenAnswer(inv -> {
                    BillingSubscription subscription = inv.getArgument(0);
                    if (subscription.getId() == null) subscription.setId(1L);
                    subscriptionHolder.set(subscription);
                    return subscription;
                });

        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(1L))
                .thenAnswer(inv -> subscriptionHolder.get().getItems());

        lenient().when(organizationsService.create(any())).thenAnswer(inv -> {
            OrganizationDTO.CreateOrganizationRequest req = inv.getArgument(0);
            return new OrganizationDTO.OrganizationResponse(
                    (long) req.code().hashCode(), req.code(), req.name(), null, null, null, null,
                    null, null, null, null, null, req.companyType(), null);
        });
    }

    private User testUser() {
        return User.builder().id(USER_ID).name("Douglas").email("douglas@example.com").build();
    }

    private OnboardingDTO.AccountUserRequest userRequest() {
        return new OnboardingDTO.AccountUserRequest(
                "Douglas", "douglas@example.com", PaymentMethodType.PIX, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    private OnboardingDTO.AccountOrganizationRequest orgRequest(String code) {
        return new OnboardingDTO.AccountOrganizationRequest(
                code, "Organização " + code, null, null, null, "32141012", null, null, null, null,
                null, CompanyType.CONDOMINIUM, SubscriptionStatus.TRIAL, null, null, null);
    }

    @Test
    void createUser_addsUserItem_withFullPlanPrice() {
        service.createUser(testUser(), userRequest());

        assertThat(subscriptionHolder.get().getItems()).hasSize(1);
        assertThat(subscriptionHolder.get().getItems().get(0).getSourceType())
                .isEqualTo(BillingSubscriptionItemSourceType.USER);
        assertThat(subscriptionHolder.get().getTotalCents()).isEqualTo(29900L);
    }

    @Test
    void createOrganization_multipleTimes_doesNotIncreaseTotalCents() {
        User user = testUser();
        service.createUser(user, userRequest());

        service.createOrganization(user, orgRequest("ORG001"));
        service.createOrganization(user, orgRequest("ORG002"));
        service.createOrganization(user, orgRequest("ORG003"));

        assertThat(subscriptionHolder.get().getItems())
                .as("1 item USER + 3 itens ORGANIZATION")
                .hasSize(4);
        assertThat(subscriptionHolder.get().getItems().stream()
                .filter(i -> i.getSourceType() == BillingSubscriptionItemSourceType.ORGANIZATION)
                .allMatch(i -> i.getValueCents() == 0L))
                .as("itens ORGANIZATION não são cobráveis")
                .isTrue();
        assertThat(subscriptionHolder.get().getTotalCents())
                .as("totalCents deve permanecer igual ao preço de um único plano, mesmo com 3 organizações")
                .isEqualTo(29900L);
    }
}
