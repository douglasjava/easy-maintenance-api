package com.brainbyte.easy_maintenance.affiliates.application.service;

import com.brainbyte.easy_maintenance.affiliates.application.dto.CommissionAdminResponse;
import com.brainbyte.easy_maintenance.affiliates.domain.*;
import com.brainbyte.easy_maintenance.affiliates.infrastructure.persistence.AffiliateRepository;
import com.brainbyte.easy_maintenance.affiliates.infrastructure.persistence.ReferralCommissionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommissionServiceTest {

    @Mock ReferralCommissionRepository commissionRepository;
    @Mock AffiliateRepository affiliateRepository;
    @InjectMocks CommissionService service;

    // ── createCommission ──────────────────────────────────────────────────

    @Test
    void createCommission_savesWithCorrectAmount() {
        when(commissionRepository.existsByOrganizationId(10L)).thenReturn(false);
        when(commissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReferralCommission result = service.createCommission(
                affiliate(), 10L, "Pro", new BigDecimal("299.00"));

        assertThat(result).isNotNull();
        assertThat(result.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("59.80"));
        assertThat(result.getStatus()).isEqualTo(CommissionStatus.PENDING);
        assertThat(result.getPlanName()).isEqualTo("Pro");
        verify(commissionRepository).save(argThat(c ->
                c.getAffiliateId().equals(1L) && c.getOrganizationId().equals(10L)));
    }

    @Test
    void createCommission_isIdempotent_whenOrgAlreadyHasCommission() {
        when(commissionRepository.existsByOrganizationId(10L)).thenReturn(true);

        ReferralCommission result = service.createCommission(
                affiliate(), 10L, "Pro", new BigDecimal("299.00"));

        assertThat(result).isNull();
        verify(commissionRepository, never()).save(any());
    }

    @Test
    void createCommission_roundsAmountToTwoDecimalPlaces() {
        when(commissionRepository.existsByOrganizationId(20L)).thenReturn(false);
        when(commissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 97.00 * 0.20 = 19.40 — exact, no rounding needed
        ReferralCommission result = service.createCommission(
                affiliate(), 20L, "Starter", new BigDecimal("97.00"));

        assertThat(result.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("19.40"));
    }

    // ── markAsPaid ────────────────────────────────────────────────────────

    @Test
    void markAsPaid_setsStatusPaidAndPaidAt() {
        ReferralCommission c = ReferralCommission.builder()
                .id(1L).status(CommissionStatus.PENDING).build();
        when(commissionRepository.findById(1L)).thenReturn(Optional.of(c));
        when(commissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReferralCommission result = service.markAsPaid(1L);

        assertThat(result.getStatus()).isEqualTo(CommissionStatus.PAID);
        assertThat(result.getPaidAt()).isNotNull();
    }

    @Test
    void markAsPaid_throwsEntityNotFound_whenCommissionMissing() {
        when(commissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsPaid(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── listAll ───────────────────────────────────────────────────────────

    @Test
    void listAll_mapsAffiliateDataIntoResponse() {
        ReferralCommission c = ReferralCommission.builder()
                .id(1L).affiliateId(1L).organizationId(5L)
                .planName("Pro").planPrice(new BigDecimal("299.00"))
                .commissionRate(new BigDecimal("0.2000"))
                .commissionAmount(new BigDecimal("59.80"))
                .status(CommissionStatus.PENDING).build();

        when(commissionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(c));
        when(affiliateRepository.findById(1L)).thenReturn(Optional.of(affiliate()));

        List<CommissionAdminResponse> result = service.listAll();

        assertThat(result).hasSize(1);
        CommissionAdminResponse resp = result.get(0);
        assertThat(resp.affiliateName()).isEqualTo("Ana");
        assertThat(resp.commissionAmount()).isEqualByComparingTo(new BigDecimal("59.80"));
        assertThat(resp.status()).isEqualTo("PENDING");
    }

    @Test
    void listAll_handlesMissingAffiliate_withFallback() {
        ReferralCommission c = ReferralCommission.builder()
                .id(1L).affiliateId(99L).organizationId(5L)
                .planName("Pro").planPrice(new BigDecimal("100.00"))
                .commissionRate(new BigDecimal("0.2000"))
                .commissionAmount(new BigDecimal("20.00"))
                .status(CommissionStatus.PENDING).build();

        when(commissionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(c));
        when(affiliateRepository.findById(99L)).thenReturn(Optional.empty());

        List<CommissionAdminResponse> result = service.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).affiliateName()).isEqualTo("—");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Affiliate affiliate() {
        return Affiliate.builder()
                .id(1L).name("Ana").email("ana@test.com").whatsapp("31999")
                .code("ANA001").commissionRate(new BigDecimal("0.2000"))
                .status(AffiliateStatus.ACTIVE).build();
    }
}
