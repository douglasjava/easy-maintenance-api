package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Role;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.shared.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsersServiceTest {

    @Mock UserRepository repository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock FirstAccessTokenService firstAccessTokenService;
    @Mock TwoFactorService twoFactorService;
    @Mock BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock BillingPlanFeaturesHelper billingPlanFeaturesHelper;
    @Mock UserOrganizationRepository userOrganizationRepository;
    @Mock BillingSubscriptionService billingSubscriptionService;

    @InjectMocks UsersService service;

    private User existingUser(Long id, String phoneNumber, boolean whatsappOptIn) {
        return User.builder()
                .id(id)
                .email("user@test.com")
                .name("Usuário Teste")
                .role(Role.ADMIN)
                .status(Status.ACTIVE)
                .phoneNumber(phoneNumber)
                .whatsappOptIn(whatsappOptIn)
                .build();
    }

    private UserDTO.UpdateUserRequest requestWith(String phoneNumber, Boolean whatsappOptIn) {
        return new UserDTO.UpdateUserRequest(
                "Usuário Teste", Role.ADMIN, Status.ACTIVE, "user@test.com", phoneNumber, whatsappOptIn);
    }

    @Test
    void updateUser_normalizesAndPersistsValidPhoneNumber() {
        User user = existingUser(1L, null, false);
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateUser(1L, requestWith("(31) 97213-9145", null));

        assertThat(response.phoneNumber()).isEqualTo("+5531972139145");
        assertThat(response.whatsappOptIn()).isFalse();
    }

    @Test
    void updateUser_rejectsInvalidPhoneNumber() {
        User user = existingUser(1L, null, false);
        when(repository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateUser(1L, requestWith("123", null)))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Telefone inválido");
    }

    @Test
    void updateUser_rejectsWhatsappOptInWithoutPhoneNumber() {
        User user = existingUser(1L, null, false);
        when(repository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateUser(1L, requestWith(null, true)))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("cadastrar um telefone");
    }

    @Test
    void updateUser_allowsWhatsappOptInWhenPhoneNumberProvidedInSameRequest() {
        User user = existingUser(1L, null, false);
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateUser(1L, requestWith("31972139145", true));

        assertThat(response.phoneNumber()).isEqualTo("+5531972139145");
        assertThat(response.whatsappOptIn()).isTrue();
    }

    @Test
    void updateUser_allowsWhatsappOptInWhenPhoneNumberAlreadyExists() {
        User user = existingUser(1L, "+5531972139145", false);
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateUser(1L, requestWith(null, true));

        assertThat(response.whatsappOptIn()).isTrue();
    }

    @Test
    void updateUser_allowsOptOutRegardlessOfPhoneNumber() {
        User user = existingUser(1L, "+5531972139145", true);
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateUser(1L, requestWith(null, false));

        assertThat(response.whatsappOptIn()).isFalse();
    }

    @Test
    void updateUser_leavesPhoneAndOptInUntouchedWhenOmitted() {
        User user = existingUser(1L, "+5531972139145", true);
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateUser(1L, requestWith(null, null));

        assertThat(response.phoneNumber()).isEqualTo("+5531972139145");
        assertThat(response.whatsappOptIn()).isTrue();
    }
}
