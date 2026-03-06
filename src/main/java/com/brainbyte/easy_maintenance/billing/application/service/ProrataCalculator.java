package com.brainbyte.easy_maintenance.billing.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class ProrataCalculator {

    public int calculateUpgradeCents(int currentPlanPriceCents, int newPlanPriceCents, Instant periodStart, Instant periodEnd) {
        
        Instant now = Instant.now();
        if (now.isAfter(periodEnd)) return newPlanPriceCents;
        if (now.isBefore(periodStart)) now = periodStart;

        long totalDurationSec = Duration.between(periodStart, periodEnd).getSeconds();
        long remainingDurationSec = Duration.between(now, periodEnd).getSeconds();

        if (totalDurationSec <= 0) return 0;

        BigDecimal totalDurationBD = BigDecimal.valueOf(totalDurationSec);
        BigDecimal remainingDurationBD = BigDecimal.valueOf(remainingDurationSec);

        // Valor proporcional não utilizado do plano atual
        BigDecimal currentPlanUnused = BigDecimal.valueOf(currentPlanPriceCents)
                .multiply(remainingDurationBD)
                .divide(totalDurationBD, 0, RoundingMode.HALF_UP);

        // Valor proporcional do novo plano até o fim do ciclo
        BigDecimal newPlanProrata = BigDecimal.valueOf(newPlanPriceCents)
                .multiply(remainingDurationBD)
                .divide(totalDurationBD, 0, RoundingMode.HALF_UP);

        BigDecimal finalAmountCents = newPlanProrata.subtract(currentPlanUnused);
        
        // Se por algum motivo o cálculo der negativo (não deveria em upgrade), retornamos 0
        return finalAmountCents.max(BigDecimal.ZERO).intValue();

    }

}
