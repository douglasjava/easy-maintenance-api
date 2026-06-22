package com.brainbyte.easy_maintenance.affiliates.application.service;

import com.brainbyte.easy_maintenance.affiliates.application.dto.CommissionAdminResponse;
import com.brainbyte.easy_maintenance.affiliates.domain.Affiliate;
import com.brainbyte.easy_maintenance.affiliates.domain.CommissionStatus;
import com.brainbyte.easy_maintenance.affiliates.domain.ReferralCommission;
import com.brainbyte.easy_maintenance.affiliates.infrastructure.persistence.AffiliateRepository;
import com.brainbyte.easy_maintenance.affiliates.infrastructure.persistence.ReferralCommissionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionService {

    private final ReferralCommissionRepository commissionRepository;
    private final AffiliateRepository affiliateRepository;

    @Transactional
    public ReferralCommission createCommission(Affiliate affiliate, Long organizationId,
                                               String planName, BigDecimal planPrice) {
        if (commissionRepository.existsByOrganizationId(organizationId)) {
            log.info("[Commission] Already exists for orgId={}, skipping (idempotent).", organizationId);
            return null;
        }
        BigDecimal amount = planPrice.multiply(affiliate.getCommissionRate())
                .setScale(2, RoundingMode.HALF_UP);
        ReferralCommission commission = ReferralCommission.builder()
                .affiliateId(affiliate.getId())
                .organizationId(organizationId)
                .planName(planName)
                .planPrice(planPrice)
                .commissionRate(affiliate.getCommissionRate())
                .commissionAmount(amount)
                .build();
        ReferralCommission saved = commissionRepository.save(commission);
        log.info("[Commission] Created: affiliateId={}, orgId={}, amount={}",
                affiliate.getId(), organizationId, amount);
        return saved;
    }

    @Transactional
    public ReferralCommission markAsPaid(Long commissionId) {
        ReferralCommission c = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Comissão não encontrada: " + commissionId));
        c.setStatus(CommissionStatus.PAID);
        c.setPaidAt(Instant.now());
        return commissionRepository.save(c);
    }

    public List<CommissionAdminResponse> listAll() {
        return commissionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(c -> {
                    Affiliate a = affiliateRepository.findById(c.getAffiliateId()).orElse(null);
                    return new CommissionAdminResponse(
                            c.getId(),
                            a != null ? a.getName() : "—",
                            a != null ? a.getEmail() : "—",
                            a != null ? a.getWhatsapp() : "—",
                            c.getOrganizationId(), c.getPlanName(), c.getPlanPrice(),
                            c.getCommissionRate(), c.getCommissionAmount(),
                            c.getStatus().name(), c.getPaidAt(), c.getCreatedAt());
                })
                .collect(Collectors.toList());
    }
}
