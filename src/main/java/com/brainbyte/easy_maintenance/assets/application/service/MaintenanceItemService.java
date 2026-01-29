package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.CreateItemRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.ItemPermissionResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.ItemResponse;
import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import com.brainbyte.easy_maintenance.assets.domain.rules.StatusCalculator;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.specification.MaintenanceItemSpecs;
import com.brainbyte.easy_maintenance.assets.mapper.IMaintenanceItemMapper;
import com.brainbyte.easy_maintenance.catalog_norms.application.service.NormService;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
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

    private final MaintenanceRepository maintenanceRepository;
    private final MaintenanceItemRepository repository;
    private final ServiceBase serviceBase;
    private final ObjectMapper objectMapper;
    private final NormService normService;

    public MaintenanceItem findById(Long itemId) {
        log.info("findById: {}", itemId);
        return repository.findById(itemId).orElseThrow(
                () -> new NotFoundException(String.format("Item not found: %s", itemId)));
    }

    public MaintenanceItem save(MaintenanceItem item) {
        log.info("save: {}", item);
        return repository.save(item);
    }

    @Transactional
    public ItemResponse create(String orgId, CreateItemRequest request) {

        validateCreate(request);

        MaintenanceItem maintenanceItem = IMaintenanceItemMapper.INSTANCE.toMaintenanceItem(orgId, request);

        maintenanceItem.setLastPerformedAt(request.lastPerformedAt());
        Period period = serviceBase.resolvePeriod(maintenanceItem);
        LocalDate base = Optional.ofNullable(maintenanceItem.getLastPerformedAt()).orElse(LocalDate.now());
        maintenanceItem.setNextDueAt(base.plus(period));
        maintenanceItem.setStatus(StatusCalculator.calculate(maintenanceItem.getNextDueAt()));
        Instant now = Instant.now();
        maintenanceItem.setCreatedAt(now);
        maintenanceItem.setUpdatedAt(now);

        repository.save(maintenanceItem);

        String normName = resolveNormName(maintenanceItem.getNormId());

        return IMaintenanceItemMapper.INSTANCE.toItemResponse(maintenanceItem, normName);

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

        String normName = resolveNormName(maintenanceItem.getNormId());

        return IMaintenanceItemMapper.INSTANCE.toItemResponse(maintenanceItem, normName);

    }

    public Page<ItemResponse> findAll(String orgId,
                                      ItemStatus status,
                                      String itemType,
                                      ItemCategory categoria,
                                      Pageable pageable) {

        Specification<MaintenanceItem> spec = MaintenanceItemSpecs.filter(orgId, status, itemType, categoria);

        return repository.findAll(spec, pageable)
                .map(item -> IMaintenanceItemMapper.INSTANCE.toItemResponse(item, resolveNormName(item.getNormId())));
    }

    public void remove(String orgId, Long itemId) {
        log.info("remove item: {}", itemId);

        MaintenanceItem maintenanceItem = findById(itemId);

        validateTenant(orgId, maintenanceItem);

        repository.deleteById(itemId);
    }

    public ItemResponse update(String orgId, Long itemId, CreateItemRequest request) {
        log.info("update item: {} - {}", itemId, request);

        MaintenanceItem maintenanceItem = findById(itemId);

        validateTenant(orgId, maintenanceItem);

        var existeMaintenance = maintenanceRepository.existsByItemId(itemId);
        if(existeMaintenance) {
            throw new ConflictException("Não é possível editar um item que possui manutenções registradas. Crie um novo item para manter o histórico.");
        }

        maintenanceItem.setItemType(request.itemType());
        maintenanceItem.setItemCategory(request.itemCategory());

        Period period = serviceBase.resolvePeriod(maintenanceItem);
        LocalDate base = Optional.ofNullable(maintenanceItem.getLastPerformedAt()).orElse(LocalDate.now());
        maintenanceItem.setNextDueAt(base.plus(period));

        maintenanceItem.setLastPerformedAt(request.lastPerformedAt());
        maintenanceItem.setCustomPeriodQty(request.customPeriodQty());
        maintenanceItem.setNormId(request.normId());
        maintenanceItem.setUpdatedAt(Instant.now());

        MaintenanceItem updatedItem = repository.save(maintenanceItem);

        return  IMaintenanceItemMapper.INSTANCE.toItemResponse(updatedItem, resolveNormName(updatedItem.getNormId()));

    }

    public ItemPermissionResponse isEditable(Long itemId) {
        var existsItemToMaintenance = maintenanceRepository.existsByItemId(itemId);
        return new ItemPermissionResponse(!existsItemToMaintenance, "ITEM_ALREADY_USED_IN_MAINTENANCE");
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
            throw new TenantException(HttpStatus.FORBIDDEN, "Item does not belong to tenant");
        }

    }

    private String resolveNormName(Long normId) {
        if (normId == null) return null;

        try {
            var normResponse = normService.findById(normId);
            return String.format("%s - %s", normResponse.itemType(), normResponse.authority());
        } catch (Exception e) {
            log.warn("Norm not found for id: {}", normId);
            return null;
        }
    }

}
