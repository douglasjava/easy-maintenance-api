package com.brainbyte.easy_maintenance.leads.application.service;

import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.leads.application.dto.CreateLeadRequest;
import com.brainbyte.easy_maintenance.leads.application.dto.LeadResponse;
import com.brainbyte.easy_maintenance.leads.domain.LandingLead;
import com.brainbyte.easy_maintenance.leads.infrastructure.persistence.LandingLeadRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock LandingLeadRepository repository;
    @Mock HttpServletRequest httpRequest;
    @InjectMocks LeadService service;

    @Test
    void createLead_savesConsentAcceptedAtFromServerClock_whenConsentIsTrue() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");
        when(repository.save(any())).thenAnswer(inv -> {
            LandingLead lead = inv.getArgument(0);
            lead.setId(1L);
            return lead;
        });

        CreateLeadRequest request = new CreateLeadRequest(
                "joao@test.com", "João", "google", "cpc", "lancamento",
                "https://google.com", "/landing", "{\"utm_source\":\"google\"}", null, true);

        Instant before = Instant.now();
        LeadResponse response = service.createLead(request, httpRequest);
        Instant after = Instant.now();

        assertThat(response.email()).isEqualTo("joao@test.com");
        verify(repository).save(argThat(lead ->
                lead.getConsentAcceptedAt() != null
                        && !lead.getConsentAcceptedAt().isBefore(before)
                        && !lead.getConsentAcceptedAt().isAfter(after)));
    }

    @Test
    void createLead_throwsRuleException_whenConsentIsFalse() {
        CreateLeadRequest request = new CreateLeadRequest(
                "joao@test.com", "João", null, null, null, null, null, null, null, false);

        assertThatThrownBy(() -> service.createLead(request, httpRequest))
                .isInstanceOf(RuleException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void createLead_throwsRuleException_whenConsentIsNull() {
        CreateLeadRequest request = new CreateLeadRequest(
                "joao@test.com", "João", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createLead(request, httpRequest))
                .isInstanceOf(RuleException.class);

        verify(repository, never()).save(any());
    }
}
