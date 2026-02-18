package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.billing.application.service.OrganizationSubscriptionService;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.domain.FirstAccessToken;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.shared.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsersServiceTest {

    @Mock
    private OrganizationsService organizationsService;
    @Mock
    private OrganizationSubscriptionService organizationSubscriptionService;
    @Mock
    private UserRepository repository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private FirstAccessTokenService firstAccessTokenService;

    @InjectMocks
    private UsersService usersService;

    private User user;
    private String currentPasswordHash = "hashedPassword";

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setPasswordHash(currentPasswordHash);
    }

    @Test
    void changePassword_ShouldThrowException_WhenNewPasswordIsEqualToCurrent() {
        // Arrange
        Long userId = 1L;
        String newPassword = "samePassword";
        UserDTO.ChangePasswordRequest request = new UserDTO.ChangePasswordRequest(userId, newPassword);

        FirstAccessToken fat = new FirstAccessToken();
        fat.setUserId(userId);
        fat.setExpiresAt(Instant.now().plusSeconds(3600));

        when(firstAccessTokenService.findByUserId(userId)).thenReturn(Optional.of(fat));
        when(repository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(newPassword, currentPasswordHash)).thenReturn(true);

        // Act & Assert
        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            usersService.changePassword(request);
        });

        assertEquals("A nova senha não pode ser igual à senha atual", exception.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void resetPassword_ShouldThrowException_WhenNewPasswordIsEqualToCurrent() {
        // Arrange
        String newPassword = "samePassword";
        when(passwordEncoder.matches(newPassword, currentPasswordHash)).thenReturn(true);

        // Act & Assert
        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            usersService.resetPassword(user, newPassword);
        });

        assertEquals("A nova senha não pode ser igual à senha atual", exception.getMessage());
        verify(repository, never()).save(any());
    }
}
