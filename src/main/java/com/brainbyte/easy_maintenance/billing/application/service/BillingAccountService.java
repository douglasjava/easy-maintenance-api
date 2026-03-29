package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAccountDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.BillingAdminDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.response.PayerSummaryResponse;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingAccountService {

    private final BillingAccountRepository repository;
    private final UserRepository userRepository;
    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    private final OrganizationRepository organizationRepository;


    public BillingAccountDTO.BillingAccountResponse findByUserId(Long userId) {
        return repository.findByUserId(userId)
                .map(IBillingMapper.INSTANCE::toBillingAccountResponse)
                .orElseThrow(() -> new NotFoundException("Billing account not found for user " + userId));
    }

    @Transactional
    public BillingAccountDTO.BillingAccountResponse updateOrCreate(Long userId, BillingAccountDTO.UpdateBillingAccountRequest request) {

        // Valida usuário
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("Usuário %s não encontrado", userId)));

        // Buscar conta existente
        var account = repository.findByUserId(userId)
                .orElseGet(() -> BillingAccount.builder().user(user).build());

        // Merge campos da conta
        mergeBillingAccountChanges(request, account);


        return IBillingMapper.INSTANCE.toBillingAccountResponse(repository.save(account));

    }


    @Transactional(readOnly = true)
    public PageResponse<BillingAdminDTO.PayerResponse> getPayersOverview(Pageable pageable) {

        Page<PayerSummaryResponse> summaryPage = repository.findPayersSummary(pageable);

        List<Long> payerIds = summaryPage.getContent().stream()
                .map(PayerSummaryResponse::userId)
                .toList();

        if (payerIds.isEmpty()) {
            return new PageResponse<>(List.of(), summaryPage.getTotalElements(),
                    summaryPage.getTotalPages(), summaryPage.getNumber(), summaryPage.getSize());
        }

        // Busca as assinaturas dos pagadores
        Map<Long, BillingSubscription> subsByPayer = billingSubscriptionRepository.findAllByBillingAccountUserIdIn(payerIds).stream()
                .collect(Collectors.toMap(s -> s.getBillingAccount().getUser().getId(), s -> s));

        List<Long> subIds = subsByPayer.values().stream().map(BillingSubscription::getId).toList();

        // Busca todos os itens das assinaturas encontradas para evitar N+1
        Map<Long, List<BillingSubscriptionItem>> itemsBySubId = billingSubscriptionItemRepository.findAllByBillingSubscriptionIdIn(subIds).stream()
                .collect(Collectors.groupingBy(item -> item.getBillingSubscription().getId()));

        // Busca organizações relacionadas para nomes e códigos
        List<String> orgCodes = itemsBySubId.values().stream()
                .flatMap(List::stream)
                .filter(i -> i.getSourceType() == BillingSubscriptionItemSourceType.ORGANIZATION)
                .map(BillingSubscriptionItem::getSourceId)
                .distinct()
                .toList();

        Map<String, Organization> orgsByCode = organizationRepository.findAllByCodeIn(orgCodes).stream()
                .collect(Collectors.toMap(Organization::getCode, o -> o));

        Page<BillingAdminDTO.PayerResponse> responsePage = summaryPage.map(summary -> {
            BillingSubscription sub = subsByPayer.get(summary.userId());
            List<BillingSubscriptionItem> items = sub != null ? itemsBySubId.getOrDefault(sub.getId(), List.of()) : List.of();

            List<BillingAdminDTO.OrganizationDetail> orgDetails = items.stream()
                    .filter(i -> i.getSourceType() == BillingSubscriptionItemSourceType.ORGANIZATION)
                    .map(i -> {
                        Organization org = orgsByCode.get(i.getSourceId());
                        return new BillingAdminDTO.OrganizationDetail(
                                org != null ? org.getId() : null,
                                i.getSourceId(),
                                org != null ? org.getName() : "N/A",
                                i.getPlan().getCode(),
                                i.getPlan().getName(),
                                i.getPlan().getPriceCents().longValue(),
                                sub.getStatus(),
                                sub.getCurrentPeriodEnd()
                        );
                    }).toList();

            BillingAdminDTO.SubscriptionDetail userSubDetail = items.stream()
                    .filter(i -> i.getSourceType() == BillingSubscriptionItemSourceType.USER)
                    .findFirst()
                    .map(i -> new BillingAdminDTO.SubscriptionDetail(
                            i.getPlan().getCode(),
                            i.getPlan().getName(),
                            i.getPlan().getPriceCents().longValue(),
                            sub.getStatus(),
                            sub.getCurrentPeriodEnd()
                    ))
                    .orElse(null);

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


    private static void mergeBillingAccountChanges(BillingAccountDTO.UpdateBillingAccountRequest request, BillingAccount account) {

        if (request.name() != null) account.setName(request.name());
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
        if (request.phone() != null) account.setPhone(request.phone());

    }

}
