package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.shared.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPlanLimitTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock FirstAccessTokenService firstAccessTokenService;
    @Mock TwoFactorService twoFactorService;
    @Mock BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock BillingPlanFeaturesHelper billingPlanFeaturesHelper;
    @Mock UserOrganizationRepository userOrganizationRepository;

    @InjectMocks UsersService service;

    private static final String ORG = "ORG-001";
    private static final Long USER_ID = 10L;

    private User userWithNoOrgs() {
        User user = User.builder().id(USER_ID).email("user@test.com").build();
        user.setOrganizations(new ArrayList<>());
        return user;
    }

    private BillingSubscriptionItem subItemWithMaxUsers(int maxUsers) {
        BillingPlan plan = BillingPlan.builder().code("STARTER").name("Starter").priceCents(4900).build();
        when(billingPlanFeaturesHelper.parse(plan))
                .thenReturn(BillingPlanFeatures.builder().maxUsers(maxUsers).build());
        return BillingSubscriptionItem.builder().plan(plan).build();
    }

    private void stubSubscription(BillingSubscriptionItem subItem) {
        when(billingSubscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.ORGANIZATION), anyList()))
                .thenReturn(List.of(subItem));
    }

    private void stubNoSubscription() {
        when(billingSubscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.ORGANIZATION), anyList()))
                .thenReturn(List.of());
    }

    @BeforeEach
    void stubUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithNoOrgs()));
    }

    // ── maxUsers: erro quando no limite ──────────────────────────────────────

    @Test
    void addOrganization_throwsRuleException_whenUserCountEqualsMaxUsers() {
        stubSubscription(subItemWithMaxUsers(3));
        when(userOrganizationRepository.countByOrganizationCode(ORG)).thenReturn(3L);

        assertThatThrownBy(() -> service.addOrganization(USER_ID, ORG))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Limite de usuários atingido")
                .hasMessageContaining("3/3");

        verify(userRepository, never()).save(any());
    }

    @Test
    void addOrganization_throwsRuleException_whenUserCountExceedsMaxUsers() {
        stubSubscription(subItemWithMaxUsers(3));
        when(userOrganizationRepository.countByOrganizationCode(ORG)).thenReturn(5L);

        assertThatThrownBy(() -> service.addOrganization(USER_ID, ORG))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Limite de usuários atingido");

        verify(userRepository, never()).save(any());
    }

    // ── maxUsers: bloqueado sem assinatura ────────────────────────────────────

    @Test
    void addOrganization_throwsRuleException_whenOrganizationHasNoSubscription() {
        stubNoSubscription();

        assertThatThrownBy(() -> service.addOrganization(USER_ID, ORG))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("assinatura");

        verify(userRepository, never()).save(any());
    }

    // ── maxUsers: permite quando abaixo do limite ─────────────────────────────

    @Test
    void addOrganization_allowsAdding_whenUserCountBelowMaxUsers() {
        stubSubscription(subItemWithMaxUsers(5));
        when(userOrganizationRepository.countByOrganizationCode(ORG)).thenReturn(2L);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.addOrganization(USER_ID, ORG))
                .doesNotThrowAnyException();

        verify(userRepository).save(any());
    }

    // ── maxUsers=0 significa ilimitado ───────────────────────────────────────

    @Test
    void addOrganization_allowsAdding_whenMaxUsersIsZero_treatedAsUnlimited() {
        stubSubscription(subItemWithMaxUsers(0));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.addOrganization(USER_ID, ORG))
                .doesNotThrowAnyException();

        // countByOrganizationCode não deve ser chamado quando maxUsers=0
        verify(userOrganizationRepository, never()).countByOrganizationCode(any());
        verify(userRepository).save(any());
    }

    // ── usuário já membro ignora limite ──────────────────────────────────────

    @Test
    void addOrganization_skipsLimitCheck_whenUserAlreadyMember() {
        // Usuário já é membro da org — deve retornar cedo sem checar limite
        User userAlreadyMember = User.builder().id(USER_ID).email("user@test.com").build();
        var uo = com.brainbyte.easy_maintenance.org_users.domain.UserOrganization.builder()
                .user(userAlreadyMember)
                .organizationCode(ORG)
                .build();
        userAlreadyMember.setOrganizations(new ArrayList<>(List.of(uo)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userAlreadyMember));

        assertThatCode(() -> service.addOrganization(USER_ID, ORG))
                .doesNotThrowAnyException();

        verifyNoInteractions(billingSubscriptionItemRepository);
        verifyNoInteractions(userOrganizationRepository);
        verify(userRepository, never()).save(any());
    }
}
