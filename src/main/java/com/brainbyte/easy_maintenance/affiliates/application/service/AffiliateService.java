package com.brainbyte.easy_maintenance.affiliates.application.service;

import com.brainbyte.easy_maintenance.affiliates.application.dto.*;
import com.brainbyte.easy_maintenance.affiliates.domain.Affiliate;
import com.brainbyte.easy_maintenance.affiliates.domain.AffiliateStatus;
import com.brainbyte.easy_maintenance.affiliates.domain.CommissionStatus;
import com.brainbyte.easy_maintenance.affiliates.domain.ReferralCommission;
import com.brainbyte.easy_maintenance.affiliates.infrastructure.persistence.AffiliateRepository;
import com.brainbyte.easy_maintenance.affiliates.infrastructure.persistence.ReferralCommissionRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.leads.infrastructure.persistence.LandingLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AffiliateService {

    private static final SecureRandom RNG = new SecureRandom();

    private static final String BASE_URL = "https://www.easymaintenance.com.br/landing?ref=";
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.2000");

    private final AffiliateRepository affiliateRepository;
    private final ReferralCommissionRepository commissionRepository;
    private final LandingLeadRepository leadRepository;

    @Transactional
    public AffiliateResponse createAffiliate(CreateAffiliateRequest request) {
        if (affiliateRepository.existsByEmail(request.email())) {
            throw new ConflictException("E-mail já cadastrado como afiliado.");
        }
        String code = generateUniqueCode();
        Affiliate affiliate = Affiliate.builder()
                .name(request.name())
                .email(request.email())
                .whatsapp(request.whatsapp())
                .code(code)
                .commissionRate(DEFAULT_COMMISSION_RATE)
                .build();
        Affiliate saved = affiliateRepository.save(affiliate);
        log.info("[Affiliate] New affiliate registered: code={}, email={}", code, request.email());
        return toResponse(saved);
    }

    public Affiliate findByCode(String code) {
        return affiliateRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Afiliado não encontrado: " + code));
    }

    public AffiliateDashboardResponse getDashboard(String code) {
        Affiliate affiliate = findByCode(code);
        var leads = leadRepository.findAllByAffiliateCode(code);
        var commissions = commissionRepository.findAllByAffiliateId(affiliate.getId());

        long converted = commissions.size();
        BigDecimal pending = commissions.stream()
                .filter(c -> c.getStatus() == CommissionStatus.PENDING)
                .map(ReferralCommission::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = commissions.stream()
                .filter(c -> c.getStatus() == CommissionStatus.PAID)
                .map(ReferralCommission::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ReferralLeadResponse> leadList = leads.stream()
                .map(l -> {
                    boolean isConverted = commissions.stream()
                            .anyMatch(c -> c.getOrganizationId() != null);
                    return new ReferralLeadResponse(
                            maskEmail(l.getEmail()),
                            isConverted ? "CONVERTED" : "LEAD",
                            l.getCreatedAt());
                })
                .collect(Collectors.toList());

        return new AffiliateDashboardResponse(
                affiliate.getName(), affiliate.getCode(),
                BASE_URL + affiliate.getCode(),
                leads.size(), converted, pending, paid, leadList);
    }

    public Optional<Affiliate> suggestForEmail(String email) {
        return leadRepository.findFirstByEmailAndAffiliateCodeIsNotNull(email)
                .flatMap(lead -> affiliateRepository.findByCode(lead.getAffiliateCode()));
    }

    public List<AffiliateResponse> listAllActive() {
        return affiliateRepository.findAllByStatus(AffiliateStatus.ACTIVE)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public AffiliateResponse toResponse(Affiliate a) {
        return new AffiliateResponse(
                a.getId(), a.getName(), a.getEmail(),
                a.getCode(), BASE_URL + a.getCode(), a.getCommissionRate());
    }

    private String generateUniqueCode() {
        String code;

        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);

            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CHARS.charAt(RNG.nextInt(CHARS.length())));
            }

            code = sb.toString();
        } while (affiliateRepository.findByCode(code).isPresent());

        return code;
    }

    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 2) return local.charAt(0) + "***@" + domain;
        return local.substring(0, 2) + "***@" + domain;
    }

}
