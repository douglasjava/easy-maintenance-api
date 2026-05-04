package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.commons.exceptions.NotAuthorizedException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void getCurrentUser_shouldReturnUser_whenAuthenticated() {
        var user = User.builder().id(1L).email("user@test.com").build();
        var auth = new UsernamePasswordAuthenticationToken("user@test.com", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        User result = authenticationService.getCurrentUser();

        assertThat(result.getEmail()).isEqualTo("user@test.com");
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getCurrentUser_shouldThrowNotAuthorizedException_whenNoAuthentication() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> authenticationService.getCurrentUser())
                .isInstanceOf(NotAuthorizedException.class)
                .hasMessageContaining("autenticado");
    }

    @Test
    void getCurrentUser_shouldThrowNotAuthorizedException_whenUserNotFoundInDb() {
        var auth = new UsernamePasswordAuthenticationToken("ghost@test.com", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.getCurrentUser())
                .isInstanceOf(NotAuthorizedException.class);
    }
}
