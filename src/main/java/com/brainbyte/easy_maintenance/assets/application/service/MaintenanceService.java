package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.RegisterMaintenanceRequest;
import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.rules.StatusCalculator;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.specification.MaintenanceSpecs;
import com.brainbyte.easy_maintenance.assets.mapper.IMaintenanceMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final MaintenanceItemService maintenanceItemService;
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


    public Page<MaintenanceResponse> listByItem(String orgId, Long itemId, LocalDate performedAt, String issuedBy, Pageable pageable) {

        if(itemId != null) {

            MaintenanceItem item = maintenanceItemService.findById(itemId);

            validateOrganization(orgId, item);
        }

        Specification<Maintenance> spec = MaintenanceSpecs.filter(orgId, itemId, performedAt, issuedBy);

        return maintenanceRepository.findAll(spec, pageable)
                .map(IMaintenanceMapper.INSTANCE::toMaintenanceResponse);

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
