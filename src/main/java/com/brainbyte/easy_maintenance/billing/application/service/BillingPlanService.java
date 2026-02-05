package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingPlanDTO;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingPlanRepository;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingPlanService {

    private final BillingPlanRepository repository;

    public List<BillingPlanDTO.BillingPlanResponse> listAll() {
        return repository.findAll().stream()
                .map(IBillingMapper.INSTANCE::toBillingPlanResponse)
                .toList();
    }

    @Transactional
    public BillingPlanDTO.BillingPlanResponse create(BillingPlanDTO.CreateBillingPlanRequest request) {
        if (repository.findByCode(request.code()).isPresent()) {
            throw new ConflictException("Billing plan with code " + request.code() + " already exists");
        }
        var plan = IBillingMapper.INSTANCE.toBillingPlan(request);
        return IBillingMapper.INSTANCE.toBillingPlanResponse(repository.save(plan));
    }

    @Transactional
    public BillingPlanDTO.BillingPlanResponse update(String code, BillingPlanDTO.UpdateBillingPlanRequest request) {
        var plan = repository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Billing plan with code " + code + " not found"));

        if (request.name() != null) plan.setName(request.name());
        if (request.currency() != null) plan.setCurrency(request.currency());
        if (request.billingCycle() != null) plan.setBillingCycle(request.billingCycle());
        if (request.priceCents() != null) plan.setPriceCents(request.priceCents());
        if (request.featuresJson() != null) plan.setFeaturesJson(request.featuresJson());
        if (request.status() != null) plan.setStatus(request.status());

        return IBillingMapper.INSTANCE.toBillingPlanResponse(repository.save(plan));
    }
}
