package com.brainbyte.easy_maintenance.assets.component;

import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.CustomPeriodUnit;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.catalog_norms.application.dto.NormDTO;
import com.brainbyte.easy_maintenance.catalog_norms.application.service.NormService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Period;

@Component
@RequiredArgsConstructor
public class ServiceBase {

    private final NormService normService;

    public Period resolvePeriod(MaintenanceItem e) {

        if (e.getItemCategory() == ItemCategory.REGULATORY) {

            NormDTO.NormResponse normResponse = normService.findById(e.getNormId());

            Integer qty = normResponse.periodQty();
            if (qty == null || qty <= 0) {
                return null;
            }

            CustomPeriodUnit periodUnit = normResponse.periodUnit();

            if (CustomPeriodUnit.ANUAL == periodUnit) {
                return Period.ofYears(qty);

            } else if (CustomPeriodUnit.MESES == periodUnit) {
                return Period.ofMonths(qty);

            } else if (CustomPeriodUnit.DIAS == periodUnit) {
                return Period.ofDays(qty);

            }

        } else {

            if (CustomPeriodUnit.ANUAL == e.getCustomPeriodUnit()) {
                return Period.ofYears(e.getCustomPeriodQty());

            } else if (CustomPeriodUnit.MESES == e.getCustomPeriodUnit()) {
                return Period.ofMonths(e.getCustomPeriodQty());

            } else if (CustomPeriodUnit.DIAS == e.getCustomPeriodUnit()) {
                return Period.ofDays(e.getCustomPeriodQty());

            }

        }

        return null;

    }

}
