package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.CreateItemRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.ItemResponse;
import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import com.brainbyte.easy_maintenance.assets.domain.rules.StatusCalculator;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.specification.MaintenanceItemSpecs;
import com.brainbyte.easy_maintenance.assets.mapper.IMaintenanceItemMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceItemService {

  private final MaintenanceItemRepository maintenanceItemRepository;
  private final ServiceBase serviceBase;
  private final ObjectMapper objectMapper;

  public MaintenanceItem findById(Long itemId) {
    log.info("findById: {}", itemId);
    return maintenanceItemRepository.findById(itemId).orElseThrow(
            () -> new NotFoundException(String.format("Item not found: %s", itemId)));
  }

  public MaintenanceItem save(MaintenanceItem item) {
    log.info("save: {}", item);
    return maintenanceItemRepository.save(item);
  }

  @Transactional
  public ItemResponse create(String orgId, CreateItemRequest request) {

    validateCreate(request);

    MaintenanceItem maintenanceItem = IMaintenanceItemMapper.INSTANCE.toMaintenanceItem(orgId, request);

    try {
      maintenanceItem.setLocationJson(isNull(request.location()) ? null : objectMapper.writeValueAsString(request.location()));
    } catch (JsonProcessingException ex) {
      throw new RuleException(String.format("Invalid location JSON %s", ex));
    }

    maintenanceItem.setLastPerformedAt(request.lastPerformedAt());
    Period period = serviceBase.resolvePeriod(maintenanceItem);
    LocalDate base = Optional.ofNullable(maintenanceItem.getLastPerformedAt()).orElse(LocalDate.now());
    maintenanceItem.setNextDueAt( base.plus(period) );
    maintenanceItem.setStatus(StatusCalculator.calculate(maintenanceItem.getNextDueAt()));
    Instant now = Instant.now();
    maintenanceItem.setCreatedAt(now);
    maintenanceItem.setUpdatedAt(now);

    maintenanceItemRepository.save(maintenanceItem);

    return IMaintenanceItemMapper.INSTANCE.toItemResponse(maintenanceItem);

  }

  @Transactional
  public List<ItemResponse> createBatch(String orgId, List<CreateItemRequest> requests) {
    return requests.stream()
            .map(req -> create(orgId, req))
            .collect(Collectors.toList());
  }


  public ItemResponse findById(String orgId, Long itemId) {

    MaintenanceItem maintenanceItem = findById(itemId);

    validateTenant(orgId, maintenanceItem);

    return IMaintenanceItemMapper.INSTANCE.toItemResponse(maintenanceItem);

  }

  public Page<ItemResponse> findAll(String orgId, ItemStatus status, String itemType, ItemCategory categoria, Pageable pageable) {

      Specification<MaintenanceItem> spec = MaintenanceItemSpecs.filter(orgId, status, itemType, categoria);

      return maintenanceItemRepository
              .findAll(spec, pageable)
              .map(IMaintenanceItemMapper.INSTANCE::toItemResponse);

  }



  private void validateCreate(CreateItemRequest request) {

    if (isNull(request.itemCategory())) {
      throw new RuleException("itemCategory must be REGULATORIA or OPERACIONAL");
    }

    if (request.itemCategory().isRegulatory()) {
      if (isNull(request.normId())) {
        throw new RuleException("normId is required and must be UUID for REGULATORIA");
      }
      if (request.customPeriodUnit() != null || request.customPeriodQty() != null) {
        throw new RuleException("customPeriod* must be null for REGULATORIA");
      }
    }

    if (request.itemCategory().isOperational()) {
      if (request.normId() != null) {
        throw new RuleException("normId must be null for OPERACIONAL");
      }
      if (request.customPeriodUnit() == null || request.customPeriodQty() == null || request.customPeriodQty() <= 0) {
        throw new RuleException("customPeriodUnit and customPeriodQty are required for OPERACIONAL");
      }
    }

  }

  private void validateTenant(String orgId, MaintenanceItem maintenanceItem) {

    if (!orgId.equals(maintenanceItem.getOrganizationCode())) {
      throw new TenantException(HttpStatus.BAD_REQUEST, "Item does not belong to tenant");
    }

  }

}
