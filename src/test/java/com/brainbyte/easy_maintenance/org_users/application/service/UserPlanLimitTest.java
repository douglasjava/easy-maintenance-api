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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @BeforeEach
    void stubUser() {
        User user = User.builder().id(USER_ID).email("user@test.com").build();
        user.setOrganizations(new ArrayList<>());
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private BillingSubscriptionItem userSubItemWithMaxOrgs(int maxOrganizations) {
        BillingPlan plan = BillingPlan.builder().code("STARTER").name("Starter").priceCents(4900).build();
        when(billingPlanFeaturesHelper.parse(plan))
                .thenReturn(BillingPlanFeatures.builder().maxOrganizations(maxOrganizations).build());
        return BillingSubscriptionItem.builder().plan(plan).build();
    }

    private void stubUserSubscription(BillingSubscriptionItem item) {
        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(
                eq(BillingSubscriptionItemSourceType.USER), eq(USER_ID.toString())))
                .thenReturn(Optional.of(item));
    }

    private void stubNoUserSubscription() {
        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(
                eq(BillingSubscriptionItemSourceType.USER), eq(USER_ID.toString())))
                .thenReturn(Optional.empty());
    }

    // ── sem assinatura → erro (não bypass silencioso) ─────────────────────────

    @Test
    void addOrganization_throwsRuleException_whenUserHasNoSubscription() {
        stubNoUserSubscription();

        assertThatThrownBy(() -> service.addOrganization(USER_ID, ORG))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("assinatura");

        verify(userRepository, never()).save(any());
    }

    // ── bloqueado quando no limite ────────────────────────────────────────────

    @Test
    void addOrganization_throwsRuleException_whenOrgCountEqualsMaxOrganizations() {
        stubUserSubscription(userSubItemWithMaxOrgs(3));
        when(userOrganizationRepository.countByUserId(USER_ID)).thenReturn(3L);

        assertThatThrownBy(() -> service.addOrganization(USER_ID, ORG))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Limite de organizações atingido")
                .hasMessageContaining("3/3");

        verify(userRepository, never()).save(any());
    }

    @Test
    void addOrganization_throwsRuleException_whenOrgCountExceedsMaxOrganizations() {
        stubUserSubscription(userSubItemWithMaxOrgs(2));
        when(userOrganizationRepository.countByUserId(USER_ID)).thenReturn(5L);

        assertThatThrownBy(() -> service.addOrganization(USER_ID, ORG))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Limite de organizações atingido");

        verify(userRepository, never()).save(any());
    }

    // ── permitido quando abaixo do limite ─────────────────────────────────────

    @Test
    void addOrganization_allowsAdding_whenOrgCountBelowMaxOrganizations() {
        stubUserSubscription(userSubItemWithMaxOrgs(3));
        when(userOrganizationRepository.countByUserId(USER_ID)).thenReturn(1L);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.addOrganization(USER_ID, ORG))
                .doesNotThrowAnyException();

        verify(userRepository).save(any());
    }

    // ── maxOrganizations=0 significa ilimitado ────────────────────────────────

    @Test
    void addOrganization_allowsAdding_whenMaxOrganizationsIsZero_treatedAsUnlimited() {
        stubUserSubscription(userSubItemWithMaxOrgs(0));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.addOrganization(USER_ID, ORG))
                .doesNotThrowAnyException();

        verify(userOrganizationRepository, never()).countByUserId(any());
        verify(userRepository).save(any());
    }

    // ── usuário já membro ignora limite ──────────────────────────────────────

    @Test
    void addOrganization_skipsLimitCheck_whenUserAlreadyMember() {
        User userAlreadyMember = User.builder().id(USER_ID).email("user@test.com").build();
        var uo = com.brainbyte.easy_maintenance.org_users.domain.UserOrganization.builder()
                .user(userAlreadyMember)
                .organizationCode(ORG)
                .build();
        userAlreadyMember.setOrganizations(new ArrayList<>(java.util.List.of(uo)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userAlreadyMember));

        assertThatCode(() -> service.addOrganization(USER_ID, ORG))
                .doesNotThrowAnyException();

        verifyNoInteractions(billingSubscriptionItemRepository);
        verifyNoInteractions(userOrganizationRepository);
        verify(userRepository, never()).save(any());
    }

    // ── validateUserLimit: dono sem assinatura → erro ─────────────────────────

    @Test
    void validateUserLimit_throwsRuleException_whenOwnerHasNoSubscription() {
        stubNoUserSubscription();

        assertThatThrownBy(() -> service.validateUserLimit(USER_ID, ORG))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("assinatura");
    }

    // ── validateUserLimit: bloqueado quando a org atingiu o limite ────────────

    @Test
    void validateUserLimit_throwsRuleException_whenOrgUserCountEqualsMaxUsers() {
        BillingPlan plan = BillingPlan.builder().code("STARTER").name("Starter").priceCents(4900).build();
        when(billingPlanFeaturesHelper.parse(plan))
                .thenReturn(BillingPlanFeatures.builder().maxUsers(3).build());
        BillingSubscriptionItem item = BillingSubscriptionItem.builder().plan(plan).build();
        stubUserSubscription(item);
        when(userOrganizationRepository.countByOrganizationCode(ORG)).thenReturn(3L);

        assertThatThrownBy(() -> service.validateUserLimit(USER_ID, ORG))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Limite de usuários atingido")
                .hasMessageContaining("3/3");
    }

    // ── validateUserLimit: permitido quando abaixo do limite ─────────────────

    @Test
    void validateUserLimit_allowsAdding_whenOrgUserCountBelowMaxUsers() {
        BillingPlan plan = BillingPlan.builder().code("STARTER").name("Starter").priceCents(4900).build();
        when(billingPlanFeaturesHelper.parse(plan))
                .thenReturn(BillingPlanFeatures.builder().maxUsers(5).build());
        BillingSubscriptionItem item = BillingSubscriptionItem.builder().plan(plan).build();
        stubUserSubscription(item);
        when(userOrganizationRepository.countByOrganizationCode(ORG)).thenReturn(2L);

        assertThatCode(() -> service.validateUserLimit(USER_ID, ORG))
                .doesNotThrowAnyException();
    }

    // ── validateUserLimit: maxUsers=0 significa ilimitado ────────────────────

    @Test
    void validateUserLimit_allowsAdding_whenMaxUsersIsZero_treatedAsUnlimited() {
        BillingPlan plan = BillingPlan.builder().code("STARTER").name("Starter").priceCents(4900).build();
        when(billingPlanFeaturesHelper.parse(plan))
                .thenReturn(BillingPlanFeatures.builder().maxUsers(0).build());
        BillingSubscriptionItem item = BillingSubscriptionItem.builder().plan(plan).build();
        stubUserSubscription(item);

        assertThatCode(() -> service.validateUserLimit(USER_ID, ORG))
                .doesNotThrowAnyException();

        verify(userOrganizationRepository, never()).countByOrganizationCode(any());
    }
}
