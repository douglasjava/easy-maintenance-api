package com.brainbyte.easy_maintenance.ai.mapper;

import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapApplyRequest;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.CustomPeriodUnit;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.LocalDate;

@Mapper
public interface IAiBootstrapMapper {

    IAiBootstrapMapper INSTANCE = Mappers.getMapper(IAiBootstrapMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizationCode", source = "organizationCode")
    @Mapping(target = "itemType", source = "item.itemType")
    @Mapping(target = "itemCategory", source = "itemCategory")
    @Mapping(target = "customPeriodUnit", source = "item.maintenance.periodUnit")
    @Mapping(target = "customPeriodQty", source = "item.maintenance.periodQty")
    @Mapping(target = "criticality", source = "item.criticality")
    @Mapping(target = "normId", source = "normId")
    @Mapping(target = "lastPerformedAt", ignore = true)
    @Mapping(target = "nextDueAt", expression = "java(calculateNextDueAt(item.getMaintenance()))")
    @Mapping(target = "status", constant = "OK")
    @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
    MaintenanceItem toMaintenanceItem(AiBootstrapApplyRequest.BootstrapApplyItem item,
                                      String organizationCode,
                                      Long normId,
                                      ItemCategory itemCategory);

    default LocalDate calculateNextDueAt(AiBootstrapApplyRequest.MaintenanceApply maintenance) {
        if (maintenance == null || maintenance.getPeriodUnit() == null || maintenance.getPeriodQty() == null) {
            return LocalDate.now();
        }
        
        LocalDate now = LocalDate.now();
        try {
            CustomPeriodUnit unit = CustomPeriodUnit.valueOf(maintenance.getPeriodUnit().toUpperCase());
            return switch (unit) {
                case DIAS -> now.plusDays(maintenance.getPeriodQty());
                case MESES -> now.plusMonths(maintenance.getPeriodQty());
                case ANUAL -> now.plusYears(maintenance.getPeriodQty());
            };
        } catch (Exception e) {
            return now;
        }
    }

    default String formatNotes(AiBootstrapApplyRequest.MaintenanceApply maintenance) {
        if (maintenance == null) return "";
        StringBuilder sb = new StringBuilder();
        if (maintenance.getNorm() != null && !maintenance.getNorm().isBlank()) {
            sb.append("Referências: ").append(maintenance.getNorm()).append("\n\n");
        }
        if (maintenance.getNotes() != null && !maintenance.getNotes().isBlank()) {
            sb.append(maintenance.getNotes());
        }
        return sb.toString().trim();
    }
}
