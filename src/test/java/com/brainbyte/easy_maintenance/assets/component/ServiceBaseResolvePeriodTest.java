package com.brainbyte.easy_maintenance.assets.component;

import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.CustomPeriodUnit;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.catalog_norms.application.dto.NormDTO;
import com.brainbyte.easy_maintenance.catalog_norms.application.service.NormService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceBaseResolvePeriodTest {

    @Mock
    NormService normService;

    @InjectMocks
    ServiceBase serviceBase;

    private MaintenanceItem regulatoryItem;

    @BeforeEach
    void setUp() {
        regulatoryItem = new MaintenanceItem();
        regulatoryItem.setItemCategory(ItemCategory.REGULATORY);
        regulatoryItem.setNormId(1L);
    }

    @Test
    void shouldReturnNullWhenNormPeriodQtyIsZero() {
        NormDTO.NormResponse norm = norm(0, CustomPeriodUnit.MESES);
        when(normService.findById(1L)).thenReturn(norm);

        Period result = serviceBase.resolvePeriod(regulatoryItem);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenNormPeriodQtyIsNull() {
        NormDTO.NormResponse norm = norm(null, CustomPeriodUnit.MESES);
        when(normService.findById(1L)).thenReturn(norm);

        Period result = serviceBase.resolvePeriod(regulatoryItem);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnMonthPeriodForMesesUnit() {
        NormDTO.NormResponse norm = norm(3, CustomPeriodUnit.MESES);
        when(normService.findById(1L)).thenReturn(norm);

        Period result = serviceBase.resolvePeriod(regulatoryItem);

        assertThat(result).isEqualTo(Period.ofMonths(3));
    }

    @Test
    void shouldReturnDayPeriodForDiasUnit() {
        NormDTO.NormResponse norm = norm(30, CustomPeriodUnit.DIAS);
        when(normService.findById(1L)).thenReturn(norm);

        Period result = serviceBase.resolvePeriod(regulatoryItem);

        assertThat(result).isEqualTo(Period.ofDays(30));
    }

    @Test
    void shouldReturnMonthPeriodForOperationalItem() {
        MaintenanceItem operational = new MaintenanceItem();
        operational.setItemCategory(ItemCategory.OPERATIONAL);
        operational.setCustomPeriodUnit(CustomPeriodUnit.MESES);
        operational.setCustomPeriodQty(6);

        Period result = serviceBase.resolvePeriod(operational);

        assertThat(result).isEqualTo(Period.ofMonths(6));
    }

    private NormDTO.NormResponse norm(Integer qty, CustomPeriodUnit unit) {
        return new NormDTO.NormResponse(null, null, unit, qty, null, null, null, null, null, null, null, null);
    }
}
