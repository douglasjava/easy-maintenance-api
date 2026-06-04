package com.brainbyte.easy_maintenance.assets.domain.rules;

import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StatusCalculatorTest {

    @Test
    void shouldReturnOkWhenNextDueIsNull() {
        assertThat(StatusCalculator.calculate(null)).isEqualTo(ItemStatus.OK);
    }

    @Test
    void shouldReturnOverdueWhenNextDueIsInThePast() {
        assertThat(StatusCalculator.calculate(LocalDate.now().minusDays(1))).isEqualTo(ItemStatus.OVERDUE);
    }

    @Test
    void shouldReturnNearDueWhenNextDueIsWithin30Days() {
        assertThat(StatusCalculator.calculate(LocalDate.now().plusDays(15))).isEqualTo(ItemStatus.NEAR_DUE);
    }

    @Test
    void shouldReturnOkWhenNextDueIsMoreThan30DaysAway() {
        assertThat(StatusCalculator.calculate(LocalDate.now().plusDays(31))).isEqualTo(ItemStatus.OK);
    }
}
