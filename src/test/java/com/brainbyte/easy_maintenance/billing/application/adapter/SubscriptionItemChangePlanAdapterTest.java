package com.brainbyte.easy_maintenance.billing.application.adapter;

import com.brainbyte.easy_maintenance.billing.application.dto.request.SubscriptionItemChangePlanRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.response.ChangePlanResponse;
import com.brainbyte.easy_maintenance.billing.application.service.UserPlanChangeService;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EPIC-014 / TASK-112 — troca de plano por organização isoladamente deixou de ser suportada;
 * apenas o item USER (plano da conta) pode ter seu plano alterado.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionItemChangePlanAdapterTest {

    @Mock UserPlanChangeService userPlanChangeService;
    @Mock BillingSubscriptionItemRepository itemRepository;

    @InjectMocks SubscriptionItemChangePlanAdapter adapter;

    private User user;
    private BillingSubscription subscription;

    @BeforeEach
    void setUp() {
        user = User.builder().id(10L).email("user@test.com").build();
        BillingAccount account = BillingAccount.builder().id(1L).user(user).build();
        subscription = BillingSubscription.builder().id(5L).billingAccount(account).build();
    }

    @Test
    void changePlan_routesToUserPlanChangeService_whenItemIsUser() {
        BillingPlan plan = BillingPlan.builder().code("BUSINESS").name("Business").priceCents(29900).build();
        BillingSubscriptionItem item = BillingSubscriptionItem.builder()
                .id(1L).billingSubscription(subscription)
                .sourceType(BillingSubscriptionItemSourceType.USER).sourceId("10")
                .plan(plan).build();

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(userPlanChangeService.changePlan(eq(10L), eq(1L), any()))
                .thenReturn(new ChangePlanResponse(ChangePlanResponse.PlanChangeType.UPGRADE, 100L, 5000, null));

        var response = adapter.changePlan(1L, user, new SubscriptionItemChangePlanRequest("ENTERPRISE", false));

        assertThat(response.subscriptionItemId()).isEqualTo(1L);
        verify(userPlanChangeService).changePlan(10L, 1L, new com.brainbyte.easy_maintenance.billing.application.dto.request.ChangePlanRequest("ENTERPRISE", false));
    }

    @Test
    void changePlan_throwsNotFound_whenItemIsOrganization() {
        BillingPlan plan = BillingPlan.builder().code("BUSINESS").name("Business").priceCents(0).build();
        BillingSubscriptionItem item = BillingSubscriptionItem.builder()
                .id(2L).billingSubscription(subscription)
                .sourceType(BillingSubscriptionItemSourceType.ORGANIZATION).sourceId("ORG-001")
                .plan(plan).build();

        when(itemRepository.findById(2L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> adapter.changePlan(2L, user, new SubscriptionItemChangePlanRequest("ENTERPRISE", false)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("organização");

        verify(userPlanChangeService, never()).changePlan(any(), any(), any());
    }

    @Test
    void changePlan_throwsNotFound_whenItemBelongsToAnotherUser() {
        User otherUser = User.builder().id(99L).build();
        BillingAccount otherAccount = BillingAccount.builder().id(2L).user(otherUser).build();
        BillingSubscription otherSubscription = BillingSubscription.builder().id(6L).billingAccount(otherAccount).build();
        BillingSubscriptionItem item = BillingSubscriptionItem.builder()
                .id(3L).billingSubscription(otherSubscription)
                .sourceType(BillingSubscriptionItemSourceType.USER).sourceId("99")
                .build();

        when(itemRepository.findById(3L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> adapter.changePlan(3L, user, new SubscriptionItemChangePlanRequest("ENTERPRISE", false)))
                .isInstanceOf(NotFoundException.class);

        verify(userPlanChangeService, never()).changePlan(any(), any(), any());
    }
}
