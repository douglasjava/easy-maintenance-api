package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Gera um arquivo .ics de um único evento (TASK-123), como lembrete manual do vencimento de um item —
 * sem OAuth, sem sincronização contínua. Ver roadmap/tasks/TASK-123.md para o desenho completo.
 */
@Service
@RequiredArgsConstructor
public class ItemCalendarExportService {

    private static final DateTimeFormatter ICS_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter ICS_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final String APP_BASE_URL = "https://www.easymaintenance.com.br";
    private static final int FOLD_LIMIT = 75;

    private final MaintenanceItemService itemService;

    public byte[] exportIcs(String orgId, Long itemId) {
        MaintenanceItem item = itemService.findEntityForOrg(orgId, itemId);

        if (item.getNextDueAt() == null) {
            throw new RuleException("Item não possui data de vencimento definida — nada para exportar.");
        }

        return buildIcs(item).getBytes(StandardCharsets.UTF_8);
    }

    private String buildIcs(MaintenanceItem item) {
        LocalDate dueDate = item.getNextDueAt();
        String uid = "item-" + item.getId() + "-" + dueDate + "@easymaintenance.com.br";
        String dtStamp = Instant.now().atZone(ZoneOffset.UTC).format(ICS_TIMESTAMP);
        String dtStart = dueDate.format(ICS_DATE);
        String dtEnd = dueDate.plusDays(1).format(ICS_DATE);
        String itemLink = APP_BASE_URL + "/items/" + item.getId();

        String description = "Categoria: " + translateCategory(item.getItemCategory())
                + "\nCriticidade: " + (item.getCriticality() != null ? item.getCriticality() : "-")
                + "\n\nEste lembrete foi gerado em " + LocalDate.now()
                + ". O status pode ter mudado desde então (manutenção concluída, adiada, etc.) —"
                + " confira o item atualizado em: " + itemLink;

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//Easy Maintenance//Item Calendar Export//PT\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("BEGIN:VEVENT\r\n");
        appendFolded(sb, "UID:" + uid);
        appendFolded(sb, "DTSTAMP:" + dtStamp);
        appendFolded(sb, "DTSTART;VALUE=DATE:" + dtStart);
        appendFolded(sb, "DTEND;VALUE=DATE:" + dtEnd);
        appendFolded(sb, "SUMMARY:" + escape("Manutenção: " + item.getItemType()));
        appendFolded(sb, "DESCRIPTION:" + escape(description));
        appendFolded(sb, "URL:" + itemLink);
        appendAlarm(sb, "P7D", "Manutenção vence em 7 dias");
        appendAlarm(sb, "P1D", "Manutenção vence amanhã");
        sb.append("END:VEVENT\r\n");
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private void appendAlarm(StringBuilder sb, String priorDuration, String message) {
        sb.append("BEGIN:VALARM\r\n");
        appendFolded(sb, "ACTION:DISPLAY");
        appendFolded(sb, "DESCRIPTION:" + escape(message));
        appendFolded(sb, "TRIGGER:-" + priorDuration);
        sb.append("END:VALARM\r\n");
    }

    private String translateCategory(ItemCategory category) {
        if (category == null) return "-";
        return switch (category) {
            case REGULATORY -> "Regulatório";
            case OPERATIONAL -> "Operacional";
        };
    }

    private String escape(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }

    /**
     * Line folding per RFC 5545 §3.1 — content lines must not exceed 75 octets;
     * continuation lines start with a single leading space.
     */
    private void appendFolded(StringBuilder sb, String contentLine) {
        if (contentLine.length() <= FOLD_LIMIT) {
            sb.append(contentLine).append("\r\n");
            return;
        }

        int pos = 0;
        boolean first = true;
        while (pos < contentLine.length()) {
            int chunkLimit = first ? FOLD_LIMIT : FOLD_LIMIT - 1;
            int end = Math.min(pos + chunkLimit, contentLine.length());
            if (!first) sb.append(" ");
            sb.append(contentLine, pos, end).append("\r\n");
            pos = end;
            first = false;
        }
    }
}
