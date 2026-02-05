package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAccountDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.response.PayerSummaryResponse;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingAccountService {

    private final BillingAccountRepository repository;
    private final UserRepository userRepository;

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

}
