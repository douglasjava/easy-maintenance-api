package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EPIC-014 / TASK-110 — modelo de plano único por conta: apenas o item USER é cobrável;
 * itens ORGANIZATION continuam existindo (para limites/consultas), mas com valueCents=0.
 */
@ExtendWith(MockitoExtension.class)
class BillingSubscriptionServiceTest {

    @Mock
    BillingSubscriptionRepository repository;
    @Mock
    BillingSubscriptionItemRepository itemRepository;
    @Mock
    AsaasClient asaasClient;
    @Mock
    BillingNotificationService billingNotificationService;
    @Mock
    BillingPlanFeaturesHelper billingPlanFeaturesHelper;
    @Mock
    MaintenanceItemRepository maintenanceItemRepository;

    @InjectMocks
    BillingSubscriptionService service;

    private BillingSubscription subscription;

    @BeforeEach
    void setUp() {
        subscription = BillingSubscription.builder()
                .id(1L)
                .status(SubscriptionStatus.TRIAL)
                .cycle(BillingCycle.MONTHLY)
                .totalCents(0L)
                .build();

        lenient().when(itemRepository.findAllByBillingSubscriptionId(1L))
                .thenAnswer(inv -> subscription.getItems());
    }

    private BillingPlan planWithPrice(String code, int priceCents) {
        return BillingPlan.builder().code(code).name(code).priceCents(priceCents).build();
    }

    @Test
    void addItem_setsFullPlanPrice_whenSourceTypeIsUser() {
        BillingPlan business = planWithPrice("BUSINESS", 29900);

        service.addItem(subscription, BillingSubscriptionItemSourceType.USER, "1", business);

        assertThat(subscription.getItems()).hasSize(1);
        assertThat(subscription.getItems().get(0).getValueCents()).isEqualTo(29900L);
        assertThat(subscription.getTotalCents()).isEqualTo(29900L);
    }

    @Test
    void addItem_setsZeroValue_whenSourceTypeIsOrganization() {
        BillingPlan business = planWithPrice("BUSINESS", 29900);

        service.addItem(subscription, BillingSubscriptionItemSourceType.ORGANIZATION, "ORG001", business);

        assertThat(subscription.getItems()).hasSize(1);
        assertThat(subscription.getItems().get(0).getValueCents()).isZero();
        assertThat(subscription.getTotalCents()).isZero();
    }

    @Test
    void addItem_totalCentsReflectsOnlyUserItem_whenMultipleOrganizationsAdded() {
        BillingPlan business = planWithPrice("BUSINESS", 29900);

        service.addItem(subscription, BillingSubscriptionItemSourceType.USER, "1", business);
        service.addItem(subscription, BillingSubscriptionItemSourceType.ORGANIZATION, "ORG001", business);
        service.addItem(subscription, BillingSubscriptionItemSourceType.ORGANIZATION, "ORG002", business);
        service.addItem(subscription, BillingSubscriptionItemSourceType.ORGANIZATION, "ORG003", business);

        assertThat(subscription.getItems()).hasSize(4);
        assertThat(subscription.getTotalCents())
                .as("totalCents deve refletir apenas o item USER, não a soma de todas as organizações")
                .isEqualTo(29900L);
    }

    @Test
    void addItem_organizationItem_keepsPlanReference_forFutureLimitChecks() {
        BillingPlan enterprise = planWithPrice("ENTERPRISE", 89900);

        service.addItem(subscription, BillingSubscriptionItemSourceType.ORGANIZATION, "ORG001", enterprise);

        assertThat(subscription.getItems().get(0).getPlan()).isEqualTo(enterprise);
        assertThat(subscription.getItems().get(0).getValueCents()).isZero();
    }

    // ── EPIC-014/TASK-116: listSubscriptions() expõe uso do pool para o painel admin ─────

    @Test
    void listSubscriptions_exposesPoolUsage_forUserAndOrganizationRows() {
        BillingPlan plan = planWithPrice("BUSINESS", 29900);
        var user = User.builder().id(10L).build();
        var account = BillingAccount.builder().id(1L).user(user).name("Douglas").build();
        subscription.setBillingAccount(account);

        BillingSubscriptionItem userItem = BillingSubscriptionItem.builder()
                .id(1L).billingSubscription(subscription)
                .sourceType(BillingSubscriptionItemSourceType.USER).sourceId("10")
                .plan(plan).valueCents(29900L).build();
        BillingSubscriptionItem orgItem = BillingSubscriptionItem.builder()
                .id(2L).billingSubscription(subscription)
                .sourceType(BillingSubscriptionItemSourceType.ORGANIZATION).sourceId("ORG-001")
                .plan(plan).valueCents(0L).build();

        Page<BillingSubscriptionItem> page = new PageImpl<>(List.of(userItem, orgItem));
        when(itemRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(itemRepository.findAllByBillingSubscriptionId(1L)).thenReturn(List.of(userItem, orgItem));
        when(billingPlanFeaturesHelper.parse(plan)).thenReturn(BillingPlanFeatures.builder().maxItems(500).build());
        when(maintenanceItemRepository.countByOrganizationCode("ORG-001")).thenReturn(7L);
        when(maintenanceItemRepository.countByOrganizationCodeIn(List.of("ORG-001"))).thenReturn(7L);

        var result = service.listSubscriptions(null, null, null, Pageable.unpaged());

        var userRow = result.content().stream()
                .filter(r -> r.sourceType() == BillingSubscriptionItemSourceType.USER).findFirst().orElseThrow();
        var orgRow = result.content().stream()
                .filter(r -> r.sourceType() == BillingSubscriptionItemSourceType.ORGANIZATION).findFirst().orElseThrow();

        assertThat(userRow.itemsUsedByOrg()).isNull();
        assertThat(userRow.itemsUsedTotalAccount()).isEqualTo(7L);
        assertThat(userRow.maxItems()).isEqualTo(500);

        assertThat(orgRow.sourceId()).isEqualTo("ORG-001");
        assertThat(orgRow.itemsUsedByOrg()).isEqualTo(7L);
        assertThat(orgRow.itemsUsedTotalAccount()).isEqualTo(7L);
        assertThat(orgRow.maxItems()).isEqualTo(500);
    }

    @Test
    void listSubscriptions_zeroesItemsUsedTotalAccount_whenAccountHasNoOrganizations() {
        BillingPlan plan = planWithPrice("STARTER", 14900);
        var user = User.builder().id(20L).build();
        var account = BillingAccount.builder().id(2L).user(user).name("Sozinho").build();
        subscription.setBillingAccount(account);

        BillingSubscriptionItem userItem = BillingSubscriptionItem.builder()
                .id(3L).billingSubscription(subscription)
                .sourceType(BillingSubscriptionItemSourceType.USER).sourceId("20")
                .plan(plan).valueCents(14900L).build();

        Page<BillingSubscriptionItem> page = new PageImpl<>(List.of(userItem));
        when(itemRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(itemRepository.findAllByBillingSubscriptionId(1L)).thenReturn(List.of(userItem));
        when(billingPlanFeaturesHelper.parse(plan)).thenReturn(BillingPlanFeatures.builder().maxItems(100).build());

        var result = service.listSubscriptions(null, null, null, Pageable.unpaged());

        var row = result.content().get(0);
        assertThat(row.itemsUsedTotalAccount()).isZero();
        assertThat(row.itemsUsedByOrg()).isNull();
    }

    // ── BUGFIX: cancelamento do item USER não pode virar status=CANCELED na hora — só o job de
    // fim de ciclo (processSubscriptionCycle) deve fazer essa transição. E o flag cancelAtPeriodEnd
    // precisa ser espelhado no item também, pois é o campo que o front lê para esconder o botão
    // "Cancelar" e mostrar o aviso de "cancelamento agendado". ──────────────────────────────────

    @Test
    void scheduleItemCancellation_userItem_flagsSubscriptionAndItem_withoutChangingStatusImmediately() {
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        BillingPlan plan = planWithPrice("BUSINESS", 29900);
        BillingSubscriptionItem userItem = BillingSubscriptionItem.builder()
                .id(1L).sourceType(BillingSubscriptionItemSourceType.USER).sourceId("10")
                .plan(plan).valueCents(29900L).build();
        subscription.addItem(userItem);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(userItem));

        service.scheduleItemCancellation(1L);

        assertThat(subscription.isCancelAtPeriodEnd()).isTrue();
        assertThat(subscription.getStatus())
                .as("status não pode mudar no pedido de cancelamento — só no fechamento do ciclo")
                .isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(userItem.isCancelAtPeriodEnd())
                .as("o item precisa refletir o agendamento — é o campo que o front usa pra esconder o botão Cancelar")
                .isTrue();
    }

    @Test
    void scheduleItemCancellation_userItem_isIdempotent_whenAlreadyScheduled() {
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCancelAtPeriodEnd(true);
        BillingPlan plan = planWithPrice("BUSINESS", 29900);
        BillingSubscriptionItem userItem = BillingSubscriptionItem.builder()
                .id(1L).sourceType(BillingSubscriptionItemSourceType.USER).sourceId("10")
                .plan(plan).valueCents(29900L).build();
        subscription.addItem(userItem);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(userItem));

        service.scheduleItemCancellation(1L);

        verify(repository, never()).save(any());
        verify(itemRepository, never()).save(any());
    }

    @Test
    void scheduleItemCancellation_organizationItem_onlyFlagsTheItem_subscriptionUntouched() {
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        BillingPlan plan = planWithPrice("BUSINESS", 29900);
        BillingSubscriptionItem orgItem = BillingSubscriptionItem.builder()
                .id(2L).sourceType(BillingSubscriptionItemSourceType.ORGANIZATION).sourceId("ORG-001")
                .plan(plan).valueCents(0L).build();
        subscription.addItem(orgItem);

        when(itemRepository.findById(2L)).thenReturn(Optional.of(orgItem));

        service.scheduleItemCancellation(2L);

        assertThat(orgItem.isCancelAtPeriodEnd()).isTrue();
        assertThat(subscription.isCancelAtPeriodEnd()).isFalse();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(repository, never()).save(any());
    }

    @Test
    void undoItemCancellation_userItem_resetsBothSubscriptionAndItemFlags() {
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCancelAtPeriodEnd(true);
        BillingPlan plan = planWithPrice("BUSINESS", 29900);
        BillingSubscriptionItem userItem = BillingSubscriptionItem.builder()
                .id(1L).sourceType(BillingSubscriptionItemSourceType.USER).sourceId("10")
                .plan(plan).valueCents(29900L).cancelAtPeriodEnd(true).build();
        subscription.addItem(userItem);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(userItem));

        service.undoItemCancellation(1L);

        assertThat(subscription.isCancelAtPeriodEnd()).isFalse();
        assertThat(userItem.isCancelAtPeriodEnd()).isFalse();
        assertThat(subscription.getStatus())
                .as("undo não precisa reverter status — o pedido de cancelamento nunca deveria tê-lo alterado")
                .isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void processSubscriptionCycle_fullCancellation_setsStatusCanceled_andMarksAllItemsCanceled() {
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCancelAtPeriodEnd(true);
        subscription.setTotalCents(29900L);
        BillingPlan plan = planWithPrice("BUSINESS", 29900);
        BillingSubscriptionItem userItem = BillingSubscriptionItem.builder()
                .id(1L).sourceType(BillingSubscriptionItemSourceType.USER).sourceId("10")
                .plan(plan).valueCents(29900L).cancelAtPeriodEnd(true).build();
        subscription.addItem(userItem);

        when(repository.findAllByNextDueDate(any(LocalDate.class))).thenReturn(List.of(subscription));

        service.processSubscriptionCycle();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(subscription.getCanceledAt()).isNotNull();
        assertThat(userItem.isCancelAtPeriodEnd()).isFalse();
        assertThat(userItem.getCanceledAt())
                .as("item precisa ficar marcado como cancelado, senão o banner de 'cancelamento agendado' fica preso para sempre")
                .isNotNull();
        verify(billingNotificationService).sendCancellationProcessedEmail(subscription);
    }

    @Test
    void processSubscriptionCycle_partialCancellation_removesOnlyFlaggedItem_keepsSubscriptionActive() {
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setTotalCents(29900L);
        BillingPlan plan = planWithPrice("BUSINESS", 29900);
        BillingSubscriptionItem userItem = BillingSubscriptionItem.builder()
                .id(1L).sourceType(BillingSubscriptionItemSourceType.USER).sourceId("10")
                .plan(plan).valueCents(29900L).build();
        BillingSubscriptionItem orgItem = BillingSubscriptionItem.builder()
                .id(2L).sourceType(BillingSubscriptionItemSourceType.ORGANIZATION).sourceId("ORG-001")
                .plan(plan).valueCents(0L).cancelAtPeriodEnd(true).build();
        subscription.addItem(userItem);
        subscription.addItem(orgItem);

        when(repository.findAllByNextDueDate(any(LocalDate.class))).thenReturn(List.of(subscription));

        service.processSubscriptionCycle();

        assertThat(subscription.getStatus())
                .as("cancelamento de item ORGANIZATION não deve afetar o status da assinatura")
                .isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(orgItem.isCancelAtPeriodEnd()).isFalse();
        assertThat(orgItem.getCanceledAt()).isNotNull();
        assertThat(userItem.getCanceledAt()).isNull();
    }
}
