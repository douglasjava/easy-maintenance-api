package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceAttachmentSimpleResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceFilter;
import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.RegisterMaintenanceRequest;
import com.brainbyte.easy_maintenance.commons.dto.CursorPageResponse;
import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceAttachment;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.rules.StatusCalculator;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.specification.MaintenanceSpecs;
import com.brainbyte.easy_maintenance.assets.mapper.IMaintenanceMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final MaintenanceItemService maintenanceItemService;
    private final MaintenanceAttachmentRepository attachmentRepository;
    private final ServiceBase serviceBase;

    @Transactional
    public MaintenanceResponse register(String orgId, Long itemId, RegisterMaintenanceRequest req) {

        MaintenanceItem item = maintenanceItemService.findById(itemId);

        if (maintenanceRepository.existsByItemIdAndPerformedAt(itemId, LocalDate.now())) {
            throw new ConflictException("Já existe uma manutenção registrada para este item na data informada");
        }

        validateRegister(orgId, req, item);

        Maintenance maintenance = IMaintenanceMapper.INSTANCE.toMaintenance(req, itemId);
        maintenanceRepository.save(maintenance);

        item.setLastPerformedAt(req.performedAt());
        Period period = serviceBase.resolvePeriod(item);
        if (period != null) {
            item.setNextDueAt(req.performedAt().plus(period));
        }
        item.setStatus(StatusCalculator.calculate(item.getNextDueAt()));
        item.setUpdatedAt(Instant.now());
        MaintenanceItem savedItem = maintenanceItemService.save(item);

        log.info("Registered maintenance for item {}: {}", itemId, savedItem);

        return IMaintenanceMapper.INSTANCE.toMaintenanceResponse(maintenance);

    }


    public Page<MaintenanceResponse> listByItem(String orgId, MaintenanceFilter filter, Pageable pageable) {

        if (filter.itemId() != null) {
            MaintenanceItem item = maintenanceItemService.findById(filter.itemId());
            validateOrganization(orgId, item);
        }

        Specification<Maintenance> spec = MaintenanceSpecs.filter(orgId, filter);

        Page<MaintenanceResponse> page = maintenanceRepository.findAll(spec, pageable)
                .map(IMaintenanceMapper.INSTANCE::toMaintenanceResponse);

        Map<Long, String> typeMap = buildItemTypeMap(page.getContent());
        return page.map(r -> withItemType(r, typeMap));
    }

    public CursorPageResponse<MaintenanceResponse> listByItemCursor(String orgId,
                                                                     MaintenanceFilter filter,
                                                                     Long cursor,
                                                                     Long prevCursor,
                                                                     int size) {
        if (filter.itemId() != null) {
            MaintenanceItem item = maintenanceItemService.findById(filter.itemId());
            validateOrganization(orgId, item);
        }

        Specification<Maintenance> baseSpec = MaintenanceSpecs.filter(orgId, filter);

        if (cursor == null && prevCursor == null) {
            // OFFSET fallback — first page
            Page<Maintenance> page = maintenanceRepository.findAll(baseSpec, PageRequest.of(0, size, Sort.by("id").ascending()));
            Long nextCursor = page.hasNext() && !page.getContent().isEmpty() ? page.getContent().getLast().getId() : null;
            List<MaintenanceResponse> content = page.getContent().stream().map(IMaintenanceMapper.INSTANCE::toMaintenanceResponse).toList();
            Map<Long, String> typeMap = buildItemTypeMap(content);
            content = content.stream().map(r -> withItemType(r, typeMap)).toList();
            return new CursorPageResponse<>(content, nextCursor, null, page.hasNext(), size, page.getTotalElements(), page.getTotalPages(), page.getNumber());
        }

        if (prevCursor != null) {
            // Backward: fetch items before prevCursor
            Specification<Maintenance> backSpec = baseSpec.and((root, query, cb) -> cb.lessThan(root.get("id"), prevCursor));
            Page<Maintenance> raw = maintenanceRepository.findAll(backSpec, PageRequest.of(0, size + 1, Sort.by("id").descending()));
            boolean hasPrev = raw.getContent().size() > size;
            List<Maintenance> items = hasPrev ? raw.getContent().subList(0, size) : raw.getContent();
            ArrayList<Maintenance> ascending = new java.util.ArrayList<>(items);
            Collections.reverse(ascending);
            List<MaintenanceResponse> content = ascending.stream().map(IMaintenanceMapper.INSTANCE::toMaintenanceResponse).toList();
            Map<Long, String> typeMap = buildItemTypeMap(content);
            content = content.stream().map(r -> withItemType(r, typeMap)).toList();
            Long pc = (hasPrev && !content.isEmpty()) ? ascending.getFirst().getId() : null;
            return CursorPageResponse.ofCursor(content, null, pc, hasPrev, size);
        }

        // Forward: fetch items after cursor
        Specification<Maintenance> fwdSpec = baseSpec.and((root, query, cb) -> cb.greaterThan(root.get("id"), cursor));
        Page<Maintenance> raw = maintenanceRepository.findAll(fwdSpec, PageRequest.of(0, size + 1, Sort.by("id").ascending()));
        boolean hasMore = raw.getContent().size() > size;
        List<Maintenance> items = hasMore ? raw.getContent().subList(0, size) : raw.getContent();
        List<MaintenanceResponse> content = items.stream().map(IMaintenanceMapper.INSTANCE::toMaintenanceResponse).toList();
        Map<Long, String> typeMap = buildItemTypeMap(content);
        content = content.stream().map(r -> withItemType(r, typeMap)).toList();
        Long nc = (hasMore && !content.isEmpty()) ? items.getLast().getId() : null;
        return CursorPageResponse.ofCursor(content, nc, null, hasMore, size);
    }

    public MaintenanceResponse findById(String orgId, Long maintenanceId) {
        log.info("Finding maintenance {} for organization {}", maintenanceId, orgId);

        Maintenance maintenance = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new NotFoundException(String.format("Manutenção não encontrada: %s", maintenanceId)));

        MaintenanceItem item = maintenanceItemService.findById(maintenance.getItemId());
        validateOrganization(orgId, item);

        List<MaintenanceAttachment> attachments = attachmentRepository.findByMaintenanceId(maintenanceId);
        List<MaintenanceAttachmentSimpleResponse> attachmentResponses = IMaintenanceMapper.INSTANCE.toAttachmentSimpleResponseList(attachments);

        MaintenanceResponse base = IMaintenanceMapper.INSTANCE.toMaintenanceResponse(maintenance, attachmentResponses);
        return new MaintenanceResponse(base.id(), base.itemId(), item.getItemType(),
                base.performedAt(), base.type(), base.performedBy(), base.costCents(), base.nextDueAt(), base.attachments());
    }


    private static void validateRegister(String orgId, RegisterMaintenanceRequest req, MaintenanceItem item) {

        validateOrganization(orgId, item);
        validatePerformedAt(req);

    }

    private Map<Long, String> buildItemTypeMap(List<MaintenanceResponse> responses) {
        Set<Long> ids = responses.stream().map(MaintenanceResponse::itemId).collect(Collectors.toSet());
        return maintenanceItemService.findAllByIds(ids).stream()
                .collect(Collectors.toMap(MaintenanceItem::getId, MaintenanceItem::getItemType));
    }

    private static MaintenanceResponse withItemType(MaintenanceResponse r, Map<Long, String> typeMap) {
        return new MaintenanceResponse(r.id(), r.itemId(), typeMap.get(r.itemId()),
                r.performedAt(), r.type(), r.performedBy(), r.costCents(), r.nextDueAt(), r.attachments());
    }

    private static void validateOrganization(String orgId, MaintenanceItem item) {

        if (!orgId.equals(item.getOrganizationCode())) {
            throw new TenantException(HttpStatus.FORBIDDEN, "Item does not belong to tenant");
        }

    }

    private static void validatePerformedAt(RegisterMaintenanceRequest req) {

        if (req.performedAt().isAfter(LocalDate.now())) {
            throw new RuleException("performedAt cannot be in the future");
        }

    }


}
