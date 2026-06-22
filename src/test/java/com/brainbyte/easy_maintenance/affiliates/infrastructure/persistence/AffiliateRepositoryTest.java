package com.brainbyte.easy_maintenance.affiliates.infrastructure.persistence;

import com.brainbyte.easy_maintenance.affiliates.domain.Affiliate;
import com.brainbyte.easy_maintenance.affiliates.domain.AffiliateStatus;
import com.brainbyte.easy_maintenance.affiliates.domain.CommissionStatus;
import com.brainbyte.easy_maintenance.affiliates.domain.ReferralCommission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AffiliateRepositoryTest {

    @Mock AffiliateRepository affiliateRepository;
    @Mock ReferralCommissionRepository commissionRepository;

    // ── AffiliateRepository ───────────────────────────────────────────────

    @Test
    void findByCode_returnsAffiliate_whenExists() {
        Affiliate a = affiliate("joao@test.com", "ABC123");
        when(affiliateRepository.findByCode("ABC123")).thenReturn(Optional.of(a));

        assertThat(affiliateRepository.findByCode("ABC123"))
                .isPresent()
                .get().extracting(Affiliate::getEmail).isEqualTo("joao@test.com");
    }

    @Test
    void findByCode_returnsEmpty_whenNotExists() {
        when(affiliateRepository.findByCode("ZZZ999")).thenReturn(Optional.empty());

        assertThat(affiliateRepository.findByCode("ZZZ999")).isEmpty();
    }

    @Test
    void existsByEmail_returnsFalse_whenNotExists() {
        when(affiliateRepository.existsByEmail("nobody@test.com")).thenReturn(false);

        assertThat(affiliateRepository.existsByEmail("nobody@test.com")).isFalse();
    }

    @Test
    void existsByEmail_returnsTrue_whenExists() {
        when(affiliateRepository.existsByEmail("ana@test.com")).thenReturn(true);

        assertThat(affiliateRepository.existsByEmail("ana@test.com")).isTrue();
    }

    @Test
    void findAllByStatus_returnsOnlyMatchingStatus() {
        Affiliate active = affiliate("active@test.com", "ACT001");
        when(affiliateRepository.findAllByStatus(AffiliateStatus.ACTIVE))
                .thenReturn(List.of(active));
        when(affiliateRepository.findAllByStatus(AffiliateStatus.INACTIVE))
                .thenReturn(List.of());

        assertThat(affiliateRepository.findAllByStatus(AffiliateStatus.ACTIVE)).hasSize(1);
        assertThat(affiliateRepository.findAllByStatus(AffiliateStatus.INACTIVE)).isEmpty();
    }

    // ── ReferralCommissionRepository ─────────────────────────────────────

    @Test
    void existsByOrganizationId_returnsFalse_whenNoCommission() {
        when(commissionRepository.existsByOrganizationId(999L)).thenReturn(false);

        assertThat(commissionRepository.existsByOrganizationId(999L)).isFalse();
    }

    @Test
    void existsByOrganizationId_returnsTrue_whenCommissionExists() {
        when(commissionRepository.existsByOrganizationId(42L)).thenReturn(true);

        assertThat(commissionRepository.existsByOrganizationId(42L)).isTrue();
    }

    @Test
    void findAllByAffiliateId_returnsOnlyMatchingAffiliate() {
        ReferralCommission c1 = commission(1L, 10L);
        ReferralCommission c2 = commission(1L, 11L);
        when(commissionRepository.findAllByAffiliateId(1L)).thenReturn(List.of(c1, c2));
        when(commissionRepository.findAllByAffiliateId(2L)).thenReturn(List.of());

        assertThat(commissionRepository.findAllByAffiliateId(1L)).hasSize(2);
        assertThat(commissionRepository.findAllByAffiliateId(2L)).isEmpty();
    }

    @Test
    void findAllByStatus_returnsOnlyPending() {
        ReferralCommission pending = commission(1L, 30L);
        when(commissionRepository.findAllByStatus(CommissionStatus.PENDING))
                .thenReturn(List.of(pending));
        when(commissionRepository.findAllByStatus(CommissionStatus.PAID))
                .thenReturn(List.of());

        assertThat(commissionRepository.findAllByStatus(CommissionStatus.PENDING)).hasSize(1)
                .first().extracting(ReferralCommission::getOrganizationId).isEqualTo(30L);
        assertThat(commissionRepository.findAllByStatus(CommissionStatus.PAID)).isEmpty();
    }

    @Test
    void findAllByOrderByCreatedAtDesc_returnsList() {
        ReferralCommission c = commission(1L, 50L);
        when(commissionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(c));

        assertThat(commissionRepository.findAllByOrderByCreatedAtDesc()).hasSize(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Affiliate affiliate(String email, String code) {
        return Affiliate.builder()
                .name("Test")
                .email(email)
                .whatsapp("31999999999")
                .code(code)
                .commissionRate(new BigDecimal("0.2000"))
                .build();
    }

    private ReferralCommission commission(Long affiliateId, Long organizationId) {
        return ReferralCommission.builder()
                .affiliateId(affiliateId)
                .organizationId(organizationId)
                .planName("Pro")
                .planPrice(new BigDecimal("299.00"))
                .commissionRate(new BigDecimal("0.2000"))
                .commissionAmount(new BigDecimal("59.80"))
                .build();
    }
}
