package com.brainbyte.easy_maintenance.affiliates.application.service;

import com.brainbyte.easy_maintenance.affiliates.application.dto.AffiliateResponse;
import com.brainbyte.easy_maintenance.affiliates.application.dto.CreateAffiliateRequest;
import com.brainbyte.easy_maintenance.affiliates.domain.Affiliate;
import com.brainbyte.easy_maintenance.affiliates.domain.AffiliateStatus;
import com.brainbyte.easy_maintenance.affiliates.infrastructure.persistence.AffiliateRepository;
import com.brainbyte.easy_maintenance.affiliates.infrastructure.persistence.ReferralCommissionRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.leads.domain.LandingLead;
import com.brainbyte.easy_maintenance.leads.infrastructure.persistence.LandingLeadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AffiliateServiceTest {

    @Mock AffiliateRepository affiliateRepository;
    @Mock ReferralCommissionRepository commissionRepository;
    @Mock LandingLeadRepository leadRepository;
    @InjectMocks AffiliateService service;

    // ── createAffiliate ───────────────────────────────────────────────────

    @Test
    void createAffiliate_savesWithGeneratedCode_andDefaultRate() {
        when(affiliateRepository.existsByEmail("joao@test.com")).thenReturn(false);
        when(affiliateRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(affiliateRepository.save(any())).thenAnswer(inv -> {
            Affiliate a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        AffiliateResponse resp = service.createAffiliate(
                new CreateAffiliateRequest("João", "joao@test.com", "31999999999"));

        assertThat(resp.code()).isNotBlank().hasSize(6)
                .matches("[A-Z0-9]{6}");
        assertThat(resp.commissionRate()).isEqualByComparingTo(new BigDecimal("0.20"));
        assertThat(resp.link()).contains("?ref=").contains(resp.code());
        verify(affiliateRepository).save(argThat(a ->
                a.getEmail().equals("joao@test.com") && a.getStatus() == AffiliateStatus.ACTIVE));
    }

    @Test
    void createAffiliate_throwsConflict_whenEmailAlreadyExists() {
        when(affiliateRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createAffiliate(
                new CreateAffiliateRequest("X", "dup@test.com", "31")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("já cadastrado");

        verify(affiliateRepository, never()).save(any());
    }

    // ── suggestForEmail ───────────────────────────────────────────────────

    @Test
    void suggestForEmail_returnsAffiliate_whenLeadHasCode() {
        LandingLead lead = new LandingLead();
        lead.setAffiliateCode("ABC123");
        Affiliate affiliate = affiliate("ana@test.com", "ABC123");

        when(leadRepository.findFirstByEmailAndAffiliateCodeIsNotNull("prospect@test.com"))
                .thenReturn(Optional.of(lead));
        when(affiliateRepository.findByCode("ABC123")).thenReturn(Optional.of(affiliate));

        assertThat(service.suggestForEmail("prospect@test.com"))
                .isPresent()
                .get().extracting(Affiliate::getCode).isEqualTo("ABC123");
    }

    @Test
    void suggestForEmail_returnsEmpty_whenNoLeadWithCode() {
        when(leadRepository.findFirstByEmailAndAffiliateCodeIsNotNull("nobody@test.com"))
                .thenReturn(Optional.empty());

        assertThat(service.suggestForEmail("nobody@test.com")).isEmpty();
    }

    // ── listAllActive ─────────────────────────────────────────────────────

    @Test
    void listAllActive_returnsOnlyActiveAffiliates() {
        when(affiliateRepository.findAllByStatus(AffiliateStatus.ACTIVE))
                .thenReturn(List.of(affiliate("a@test.com", "AAA111")));

        List<AffiliateResponse> result = service.listAllActive();

        assertThat(result).hasSize(1)
                .first().extracting(AffiliateResponse::code).isEqualTo("AAA111");
    }

    // ── maskEmail (static utility) ────────────────────────────────────────

    @Test
    void maskEmail_masksLocalPart_keepingFirstTwoChars() {
        assertThat(AffiliateService.maskEmail("joao@gmail.com")).isEqualTo("jo***@gmail.com");
    }

    @Test
    void maskEmail_shortLocal_masksAfterFirstChar() {
        assertThat(AffiliateService.maskEmail("ab@test.com")).isEqualTo("a***@test.com");
    }

    @Test
    void maskEmail_nullInput_returnsFallback() {
        assertThat(AffiliateService.maskEmail(null)).isEqualTo("***");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Affiliate affiliate(String email, String code) {
        return Affiliate.builder()
                .id(1L).name("Test").email(email).whatsapp("31999")
                .code(code).commissionRate(new BigDecimal("0.2000"))
                .status(AffiliateStatus.ACTIVE).build();
    }
}
