package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.request.ChangePlanRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.response.ChangePlanResponse;
import com.brainbyte.easy_maintenance.billing.domain.*;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.infrastructure.saas.properties.AsaasProperties;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPlanChangeServiceTest {

    @Mock private BillingPlanRepository planRepository;
    @Mock private ProrataCalculator prorataCalculator;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private AuditService auditService;
    @Mock private ObjectMapper objectMapper;
    @Mock private BillingSubscriptionRepository billingSubscriptionRepository;
    @Mock private BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock private AsaasClient asaasClient;
    @Mock private BillingPlanFeaturesHelper featuresHelper;
    @Mock private AsaasProperties asaasProperties;
    @Mock private UserOrganizationRepository userOrganizationRepository;

    @InjectMocks
    private UserPlanChangeService service;

    private BillingPlan currentPlan;
    private BillingPlan cheaperPlan;
    private BillingPlan expensivePlan;
    private BillingAccount billingAccount;
    private BillingSubscription subscription;
    private BillingSubscriptionItem item;

    @BeforeEach
    void setUp() {
        currentPlan = BillingPlan.builder().id(1L).code("BASIC").name("Básico").priceCents(9900).build();
        cheaperPlan = BillingPlan.builder().id(2L).code("FREE").name("Grátis").priceCents(0).build();
        expensivePlan = BillingPlan.builder().id(3L).code("PRO").name("Pro").priceCents(19900).build();

        var user = User.builder().id(10L).email("user@test.com").build();
        billingAccount = BillingAccount.builder()
                .id(1L).user(user).externalCustomerId("asaas-cust-001").build();

        subscription = BillingSubscription.builder()
                .id(5L)
                .billingAccount(billingAccount)
                .status(SubscriptionStatus.ACTIVE)
                .cycle(BillingCycle.MONTHLY)
                .currentPeriodStart(Instant.now().minus(15, ChronoUnit.DAYS))
                .currentPeriodEnd(Instant.now().plus(15, ChronoUnit.DAYS))
                .build();

        item = BillingSubscriptionItem.builder()
                .id(1L)
                .plan(currentPlan)
                .billingSubscription(subscription)
                .sourceId("user-10")
                .valueCents(9900L)
                .build();
    }

    @Test
    void changePlan_upgrade_shouldCreateInvoiceAndPayment() {
        var request = new ChangePlanRequest("PRO", false);

        when(billingSubscriptionItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(expensivePlan));
        when(prorataCalculator.calculateUpgradeCents(anyInt(), anyInt(), any(), any())).thenReturn(5000);
        when(invoiceRepository.save(any())).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            i.setId(100L);
            return i;
        });
        when(asaasClient.createCheckout(any())).thenReturn(
                new AsaasDTO.CheckoutResponse("chk-001", "https://checkout.link", null, null));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billingSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billingSubscriptionItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(asaasProperties.checkoutMinutesToExpire()).thenReturn(30);
        when(asaasProperties.checkoutSuccessUrl()).thenReturn("https://success");
        when(asaasProperties.checkoutCancelUrl()).thenReturn("https://cancel");
        when(asaasProperties.checkoutExpiredUrl()).thenReturn("https://expired");

        ChangePlanResponse response = service.changePlan(10L, 1L, request);

        assertThat(response.type()).isEqualTo(ChangePlanResponse.PlanChangeType.UPGRADE);
        assertThat(response.invoiceId()).isNotNull();
        assertThat(response.amountCharged()).isEqualTo(5000);

        verify(invoiceRepository).save(any());
        verify(paymentRepository).save(any());
        verify(billingSubscriptionRepository).save(any());
        verify(billingSubscriptionItemRepository).save(any());
    }

    @Test
    void changePlan_downgrade_shouldScheduleForNextPeriod() {
        var request = new ChangePlanRequest("FREE", false);
        var features = BillingPlanFeatures.builder().maxOrganizations(10).build();

        when(billingSubscriptionItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(cheaperPlan));
        when(userOrganizationRepository.countByUserId(10L)).thenReturn(1L);
        when(featuresHelper.parse(cheaperPlan)).thenReturn(features);
        when(billingSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billingSubscriptionItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangePlanResponse response = service.changePlan(10L, 1L, request);

        assertThat(response.type()).isEqualTo(ChangePlanResponse.PlanChangeType.DOWNGRADE);
        assertThat(response.invoiceId()).isNull();
        assertThat(response.effectiveAt()).isNotNull();

        verify(invoiceRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void changePlan_applyImmediately_shouldForceUpgrade() {
        var request = new ChangePlanRequest("FREE", true);

        when(billingSubscriptionItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(cheaperPlan));
        when(prorataCalculator.calculateUpgradeCents(anyInt(), anyInt(), any(), any())).thenReturn(0);
        when(invoiceRepository.save(any())).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            i.setId(101L);
            return i;
        });
        when(asaasClient.createCheckout(any())).thenReturn(
                new AsaasDTO.CheckoutResponse("chk-002", "https://link2", null, null));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billingSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billingSubscriptionItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(asaasProperties.checkoutMinutesToExpire()).thenReturn(30);
        when(asaasProperties.checkoutSuccessUrl()).thenReturn("https://success");
        when(asaasProperties.checkoutCancelUrl()).thenReturn("https://cancel");
        when(asaasProperties.checkoutExpiredUrl()).thenReturn("https://expired");

        ChangePlanResponse response = service.changePlan(10L, 1L, request);

        assertThat(response.type()).isEqualTo(ChangePlanResponse.PlanChangeType.UPGRADE);
    }

    @Test
    void changePlan_sameCode_shouldThrowRuleException() {
        var request = new ChangePlanRequest("BASIC", false);

        when(billingSubscriptionItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(planRepository.findByCode("BASIC")).thenReturn(Optional.of(currentPlan));

        assertThatThrownBy(() -> service.changePlan(10L, 1L, request))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("igual");
    }

    @Test
    void changePlan_subscriptionNotActive_shouldThrowRuleException() {
        subscription.setStatus(SubscriptionStatus.CANCELED);
        var request = new ChangePlanRequest("PRO", false);

        when(billingSubscriptionItemRepository.findById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.changePlan(10L, 1L, request))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("ATIVA");
    }

    @Test
    void changePlan_itemNotFound_shouldThrowNotFoundException() {
        when(billingSubscriptionItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePlan(10L, 99L, new ChangePlanRequest("PRO", false)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void changePlan_downgrade_exceedsPlanLimits_shouldThrowRuleException() {
        var request = new ChangePlanRequest("FREE", false);
        var features = BillingPlanFeatures.builder().maxOrganizations(1).build();

        when(billingSubscriptionItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(cheaperPlan));
        when(userOrganizationRepository.countByUserId(10L)).thenReturn(5L);
        when(featuresHelper.parse(cheaperPlan)).thenReturn(features);

        assertThatThrownBy(() -> service.changePlan(10L, 1L, request))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("organiza");
    }
}
