package com.brainbyte.easy_maintenance.assets.component;

import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.CustomPeriodUnit;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.catalog_norms.application.dto.NormDTO;
import com.brainbyte.easy_maintenance.catalog_norms.application.service.NormService;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
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
        throw new RuleException("Invalid period in norm");
      }

      CustomPeriodUnit periodUnit = normResponse.periodUnit();
      return CustomPeriodUnit.MESES == periodUnit ? Period.ofMonths(qty) : Period.ofDays(qty);

    } else {
      return CustomPeriodUnit.MESES == e.getCustomPeriodUnit()
              ? Period.ofMonths(e.getCustomPeriodQty())
              : Period.ofDays(e.getCustomPeriodQty());
    }

  }

}
