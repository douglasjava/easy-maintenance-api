package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAccountDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.BillingAdminDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.response.PayerSummaryResponse;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.OrganizationSubscription;
import com.brainbyte.easy_maintenance.billing.domain.UserSubscription;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.OrganizationSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.UserSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingAccountService {

    private final UserSubscriptionService userSubscriptionService;
    private final BillingAccountRepository repository;
    private final UserRepository userRepository;
    private final OrganizationSubscriptionRepository organizationSubscriptionRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;

    public BillingAccountDTO.BillingAccountResponse findByUserId(Long userId) {
        return repository.findByUserId(userId)
                .map(IBillingMapper.INSTANCE::toBillingAccountResponse)
                .orElseThrow(() -> new NotFoundException("Billing account not found for user " + userId));
    }

    @Transactional
    public BillingAccountDTO.BillingAccountResponse updateOrCreate(Long userId, BillingAccountDTO.UpdateBillingAccountRequest request) {
        var account = repository.findByUserId(userId)
                .orElseGet(() -> {
                    var user = userRepository.findById(userId)
                            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
                    return BillingAccount.builder().user(user).build();
                });

        if (request.billingEmail() != null) account.setBillingEmail(request.billingEmail());
        if (request.paymentMethod() != null) account.setPaymentMethod(request.paymentMethod());
        if (request.doc() != null) account.setDoc(request.doc());
        if (request.street() != null) account.setStreet(request.street());
        if (request.number() != null) account.setNumber(request.number());
        if (request.complement() != null) account.setComplement(request.complement());
        if (request.neighborhood() != null) account.setNeighborhood(request.neighborhood());
        if (request.city() != null) account.setCity(request.city());
        if (request.state() != null) account.setState(request.state());
        if (request.zipCode() != null) account.setZipCode(request.zipCode());
        if (request.country() != null) account.setCountry(request.country());
        if (request.status() != null) account.setStatus(request.status());

        return IBillingMapper.INSTANCE.toBillingAccountResponse(repository.save(account));
    }

    public List<PayerSummaryResponse> getTopPayers() {
        return repository.findTopPayers();
    }

    public PageResponse<BillingAdminDTO.PayerResponse> getPayersOverview(Pageable pageable) {

        Page<PayerSummaryResponse> summaryPage = repository.findPayersSummary(pageable);

        List<Long> payerIds = summaryPage.getContent().stream()
                .map(PayerSummaryResponse::userId)
                .toList();

        if (payerIds.isEmpty()) {
            return new PageResponse<>(List.of(), summaryPage.getTotalElements(),
                    summaryPage.getTotalPages(), summaryPage.getNumber(), summaryPage.getSize());
        }

        List<OrganizationSubscription> orgSubscriptions = organizationSubscriptionRepository.findAllByPayerIdIn(payerIds);
        List<UserSubscription> userSubscriptions = userSubscriptionRepository.findAllByUserIdIn(payerIds);

        Map<Long, List<OrganizationSubscription>> orgSubsByPayer = orgSubscriptions.stream()
                .collect(Collectors.groupingBy(os -> os.getPayer().getId()));

        Map<Long, UserSubscription> userSubByPayer = userSubscriptions.stream()
                .collect(Collectors.toMap(us -> us.getUser().getId(), us -> us));

        Page<BillingAdminDTO.PayerResponse> responsePage = summaryPage.map(summary -> {
            List<OrganizationSubscription> payerOrgSubs = orgSubsByPayer.getOrDefault(summary.userId(), List.of());
            UserSubscription payerUserSub = userSubByPayer.get(summary.userId());

            List<BillingAdminDTO.OrganizationDetail> orgDetails = payerOrgSubs.stream()
                    .map(os -> new BillingAdminDTO.OrganizationDetail(
                            os.getOrganization().getId(),
                            os.getOrganization().getCode(),
                            os.getOrganization().getName(),
                            os.getPlan().getCode(),
                            os.getPlan().getName(),
                            os.getPlan().getPriceCents().longValue(),
                            os.getStatus(),
                            os.getCurrentPeriodEnd()
                    )).toList();

            BillingAdminDTO.SubscriptionDetail userSubDetail = null;
            if (payerUserSub != null) {
                userSubDetail = new BillingAdminDTO.SubscriptionDetail(
                        payerUserSub.getPlan().getCode(),
                        payerUserSub.getPlan().getName(),
                        payerUserSub.getPlan().getPriceCents().longValue(),
                        payerUserSub.getStatus(),
                        payerUserSub.getCurrentPeriodEnd()
                );
            }

            long userCents = summary.userSubscriptionPriceCents();
            long orgsCents = summary.organizationSubscriptionPriceCents();
            long totalCents = summary.organizationSubscriptionPriceCents() + summary.userSubscriptionPriceCents();

            return new BillingAdminDTO.PayerResponse(
                    summary.userId(),
                    summary.name(),
                    summary.email(),
                    totalCents,
                    summary.orgCount().intValue(),
                    userSubDetail,
                    orgDetails,
                    new BillingAdminDTO.RevenueDetail(userCents, orgsCents, totalCents)
            );
        });

        return PageResponse.of(responsePage);
    }
}
