package com.brainbyte.easy_maintenance.assets.domain.rules;

import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import lombok.experimental.UtilityClass;

import java.time.LocalDate;

@UtilityClass
public final class StatusCalculator {

  public static ItemStatus calculate(LocalDate nextDue) {

    LocalDate today = LocalDate.now();

    if (nextDue.isBefore(today)) {
      return ItemStatus.OVERDUE;
    }

    if (!nextDue.isAfter(today.plusDays(30))) {
      return ItemStatus.NEAR_DUE;
    }

    return ItemStatus.OK;

  }

}
