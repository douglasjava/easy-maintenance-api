package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceAttachmentSimpleResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.RegisterMaintenanceRequest;
import com.brainbyte.easy_maintenance.commons.dto.CursorPageResponse;
import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceAttachment;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
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
        item.setNextDueAt(req.performedAt().plus(period));
        item.setStatus(StatusCalculator.calculate(item.getNextDueAt()));
        item.setUpdatedAt(Instant.now());
        MaintenanceItem savedItem = maintenanceItemService.save(item);

        log.info("Registered maintenance for item {}: {}", itemId, savedItem);

        return IMaintenanceMapper.INSTANCE.toMaintenanceResponse(maintenance);

    }


    public Page<MaintenanceResponse> listByItem(String orgId, Long itemId, LocalDate performedAt, MaintenanceType type, String performedBy, Pageable pageable) {

        if(itemId != null) {

            MaintenanceItem item = maintenanceItemService.findById(itemId);

            validateOrganization(orgId, item);
        }

        Specification<Maintenance> spec = MaintenanceSpecs.filter(orgId, itemId, performedAt, type, performedBy);

        return maintenanceRepository.findAll(spec, pageable)
                .map(IMaintenanceMapper.INSTANCE::toMaintenanceResponse);

    }

    public CursorPageResponse<MaintenanceResponse> listByItemCursor(String orgId, Long itemId,
                                                                     LocalDate performedAt,
                                                                     MaintenanceType type,
                                                                     String performedBy,
                                                                     Long cursor,
                                                                     Long prevCursor,
                                                                     int size) {
        if (itemId != null) {
            MaintenanceItem item = maintenanceItemService.findById(itemId);
            validateOrganization(orgId, item);
        }

        Specification<Maintenance> baseSpec = MaintenanceSpecs.filter(orgId, itemId, performedAt, type, performedBy);

        if (cursor == null && prevCursor == null) {
            // OFFSET fallback — first page
            Page<Maintenance> page = maintenanceRepository.findAll(
                    baseSpec, PageRequest.of(0, size, Sort.by("id").ascending()));
            Long nextCursor = page.hasNext() && !page.getContent().isEmpty()
                    ? page.getContent().get(page.getContent().size() - 1).getId()
                    : null;
            List<MaintenanceResponse> content = page.getContent().stream()
                    .map(IMaintenanceMapper.INSTANCE::toMaintenanceResponse).toList();
            return new CursorPageResponse<>(content, nextCursor, null, page.hasNext(),
                    size, page.getTotalElements(), page.getTotalPages(), page.getNumber());
        }

        if (prevCursor != null) {
            // Backward: fetch items before prevCursor
            Specification<Maintenance> backSpec = baseSpec.and(
                    (root, query, cb) -> cb.lessThan(root.get("id"), prevCursor));
            Page<Maintenance> raw = maintenanceRepository.findAll(
                    backSpec, PageRequest.of(0, size + 1, Sort.by("id").descending()));
            boolean hasPrev = raw.getContent().size() > size;
            List<Maintenance> items = hasPrev ? raw.getContent().subList(0, size) : raw.getContent();
            java.util.ArrayList<Maintenance> ascending = new java.util.ArrayList<>(items);
            java.util.Collections.reverse(ascending);
            List<MaintenanceResponse> content = ascending.stream()
                    .map(IMaintenanceMapper.INSTANCE::toMaintenanceResponse).toList();
            Long pc = (hasPrev && !content.isEmpty())
                    ? ascending.get(0).getId() : null;
            return CursorPageResponse.ofCursor(content, null, pc, hasPrev, size);
        }

        // Forward: fetch items after cursor
        Specification<Maintenance> fwdSpec = baseSpec.and(
                (root, query, cb) -> cb.greaterThan(root.get("id"), cursor));
        Page<Maintenance> raw = maintenanceRepository.findAll(
                fwdSpec, PageRequest.of(0, size + 1, Sort.by("id").ascending()));
        boolean hasMore = raw.getContent().size() > size;
        List<Maintenance> items = hasMore ? raw.getContent().subList(0, size) : raw.getContent();
        List<MaintenanceResponse> content = items.stream()
                .map(IMaintenanceMapper.INSTANCE::toMaintenanceResponse).toList();
        Long nc = (hasMore && !content.isEmpty()) ? items.get(items.size() - 1).getId() : null;
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

        return IMaintenanceMapper.INSTANCE.toMaintenanceResponse(maintenance, attachmentResponses);
    }


    private static void validateRegister(String orgId, RegisterMaintenanceRequest req, MaintenanceItem item) {

        validateOrganization(orgId, item);
        validatePerformedAt(req);

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
