package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceExportProjection;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.commons.exceptions.NotAuthorizedException;
import com.brainbyte.easy_maintenance.infrastructure.access.application.service.SubscriptionAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceExportService {

    private final MaintenanceRepository maintenanceRepository;
    private final SubscriptionAccessService subscriptionAccessService;
    private final BillingPlanFeaturesHelper featuresHelper;

    public byte[] exportCsv(String orgCode, Long itemId, LocalDate startDate, LocalDate endDate) {
        checkReportsFeature(orgCode);

        List<MaintenanceExportProjection> rows =
                maintenanceRepository.findForExport(orgCode, itemId, startDate, endDate);

        log.info("[Export] org={} itemId={} rows={}", orgCode, itemId, rows.size());

        return buildCsv(rows);
    }

    private void checkReportsFeature(String orgCode) {
        boolean reportsEnabled = subscriptionAccessService
                .getOrganizationSubscriptionItem(orgCode)
                .map(item -> featuresHelper.parse(item.getPlan()))
                .map(f -> f.isReportsEnabled())
                .orElse(false);

        if (!reportsEnabled) {
            throw new NotAuthorizedException(
                    "Seu plano não permite exportação de relatórios. Faça upgrade para habilitar esta funcionalidade.");
        }
    }

    private byte[] buildCsv(List<MaintenanceExportProjection> rows) {
        var sb = new StringBuilder();
        sb.append("ID,Item,Data da Manutenção,Tipo,Responsável,Custo (R$),Próxima Data,Norma Aplicável\n");

        for (var row : rows) {
            sb.append(row.getId()).append(",");
            sb.append(csv(row.getItemType())).append(",");
            sb.append(dateStr(row.getPerformedAt())).append(",");
            sb.append(csv(row.getMaintenanceType())).append(",");
            sb.append(csv(row.getPerformedBy())).append(",");
            sb.append(formatCost(row.getCostCents())).append(",");
            sb.append(dateStr(row.getNextDueAt())).append(",");
            sb.append(csv(row.getNormAuthority())).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String dateStr(LocalDate date) {
        return date != null ? date.toString() : "";
    }

    private String formatCost(Integer costCents) {
        if (costCents == null || costCents == 0) return "";
        return String.format("%.2f", costCents / 100.0).replace(".", ",");
    }
}
