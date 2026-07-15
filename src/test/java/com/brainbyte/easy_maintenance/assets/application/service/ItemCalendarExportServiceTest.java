package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemCalendarExportServiceTest {

    @Mock
    private MaintenanceItemService itemService;

    @InjectMocks
    private ItemCalendarExportService service;

    private static final String ORG = "ORG-1";

    private MaintenanceItem buildItem(Long id, LocalDate nextDueAt, ItemCategory category, String criticality) {
        return MaintenanceItem.builder()
                .id(id)
                .organizationCode(ORG)
                .itemType("Extintor Bloco A")
                .itemCategory(category)
                .criticality(criticality)
                .nextDueAt(nextDueAt)
                .build();
    }

    @Test
    void shouldGenerateValidIcsWithTwoAlarms() {
        MaintenanceItem item = buildItem(10L, LocalDate.of(2026, 8, 1), ItemCategory.REGULATORY, "ALTA");
        when(itemService.findEntityForOrg(ORG, 10L)).thenReturn(item);

        byte[] result = service.exportIcs(ORG, 10L);
        String ics = new String(result, StandardCharsets.UTF_8);

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("BEGIN:VEVENT\r\n");
        assertThat(ics).contains("DTSTART;VALUE=DATE:20260801\r\n");
        assertThat(ics).contains("DTEND;VALUE=DATE:20260802\r\n");
        assertThat(ics).contains("SUMMARY:Manutenção: Extintor Bloco A\r\n");
        assertThat(ics).contains("URL:https://www.easymaintenance.com.br/items/10\r\n");

        // 2 VALARM blocks: 7 days before and 1 day before
        assertThat(countOccurrences(ics, "BEGIN:VALARM")).isEqualTo(2);
        assertThat(ics).contains("TRIGGER:-P7D\r\n");
        assertThat(ics).contains("TRIGGER:-P1D\r\n");

        // Description carries category + criticality (escaped newlines per RFC 5545)
        assertThat(ics).contains("Categoria: Regulatório");
        assertThat(ics).contains("Criticidade: ALTA");
    }

    @Test
    void shouldThrowWhenNextDueAtIsNull() {
        MaintenanceItem item = buildItem(11L, null, ItemCategory.OPERATIONAL, "BAIXA");
        when(itemService.findEntityForOrg(ORG, 11L)).thenReturn(item);

        assertThatThrownBy(() -> service.exportIcs(ORG, 11L))
                .isInstanceOf(RuleException.class);
    }

    @Test
    void shouldPropagateTenantExceptionFromWrongOrg() {
        when(itemService.findEntityForOrg(ORG, 12L))
                .thenThrow(new TenantException(HttpStatus.FORBIDDEN, "Item não pertence a essa organização"));

        assertThatThrownBy(() -> service.exportIcs(ORG, 12L))
                .isInstanceOf(TenantException.class);
    }

    @Test
    void shouldFoldLongContentLines() {
        String longCriticality = "A".repeat(120);
        MaintenanceItem item = buildItem(13L, LocalDate.of(2026, 9, 1), ItemCategory.OPERATIONAL, longCriticality);
        when(itemService.findEntityForOrg(ORG, 13L)).thenReturn(item);

        String ics = new String(service.exportIcs(ORG, 13L), StandardCharsets.UTF_8);

        for (String line : ics.split("\r\n")) {
            assertThat(line.length()).isLessThanOrEqualTo(75);
        }
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
