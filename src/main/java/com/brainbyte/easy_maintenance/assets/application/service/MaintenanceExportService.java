package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.CrossOrgMaintenanceExportProjection;
import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceExportProjection;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotAuthorizedException;
import com.brainbyte.easy_maintenance.infrastructure.access.application.service.SubscriptionAccessService;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceExportService {

    private final MaintenanceRepository maintenanceRepository;
    private final SubscriptionAccessService subscriptionAccessService;
    private final BillingPlanFeaturesHelper featuresHelper;
    private final UserOrganizationRepository userOrgRepository;
    private final OrganizationRepository organizationRepository;
    private final BillingSubscriptionItemRepository subscriptionItemRepository;

    public byte[] exportCsv(String orgCode, Long itemId, LocalDate startDate, LocalDate endDate) {
        checkReportsFeature(orgCode);

        List<MaintenanceExportProjection> rows =
                maintenanceRepository.findForExport(orgCode, itemId, startDate, endDate);

        log.info("[Export] org={} itemId={} rows={}", orgCode, itemId, rows.size());

        return buildCsv(rows);
    }

    public byte[] exportCsvCrossOrg(Long userId, List<String> requestedOrgCodes,
                                     LocalDate startDate, LocalDate endDate) {
        List<String> userOrgCodes = userOrgRepository.findAllByUserId(userId).stream()
                .map(UserOrganization::getOrganizationCode)
                .toList();

        List<String> effective = requestedOrgCodes == null || requestedOrgCodes.isEmpty()
                ? userOrgCodes
                : userOrgCodes.stream().filter(requestedOrgCodes::contains).toList();

        if (effective.isEmpty()) {
            throw new NotAuthorizedException(
                    "Nenhuma das suas empresas possui o plano com exportação de relatórios habilitada.");
        }

        // Batch-check reportsEnabled — only export from orgs where the plan allows it
        List<String> authorizedOrgCodes = subscriptionItemRepository
                .findAllBySourceTypeAndSourceIdIn(BillingSubscriptionItemSourceType.ORGANIZATION, effective)
                .stream()
                .filter(item -> featuresHelper.parse(item.getPlan()).isReportsEnabled())
                .map(BillingSubscriptionItem::getSourceId)
                .toList();

        if (authorizedOrgCodes.isEmpty()) {
            throw new NotAuthorizedException(
                    "Nenhuma das suas empresas possui o plano com exportação de relatórios habilitada.");
        }

        Map<String, String> orgNames = organizationRepository.findAllByCodeIn(authorizedOrgCodes).stream()
                .collect(Collectors.toMap(Organization::getCode, Organization::getName));

        List<CrossOrgMaintenanceExportProjection> rows =
                maintenanceRepository.findForExportCrossOrg(authorizedOrgCodes, startDate, endDate);

        log.info("[CrossOrgExport] userId={} orgs={} rows={}", userId, authorizedOrgCodes, rows.size());

        return buildCsvCrossOrg(rows, orgNames);
    }

    private byte[] buildCsvCrossOrg(List<CrossOrgMaintenanceExportProjection> rows, Map<String, String> orgNames) {
        var sb = new StringBuilder();
        sb.append('﻿'); // UTF-8 BOM — required for Excel on Windows to decode accents correctly
        sb.append("ID,Empresa,Item,Data da Manutenção,Tipo,Responsável,Custo (R$),Próxima Data,Norma Aplicável\n");

        for (var row : rows) {
            sb.append(row.getId()).append(",");
            sb.append(csv(orgNames.getOrDefault(row.getOrgCode(), row.getOrgCode()))).append(",");
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
        sb.append('﻿'); // UTF-8 BOM — required for Excel on Windows to decode accents correctly
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

    private static final java.text.NumberFormat COST_FORMAT =
            java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("pt", "BR"));

    private String formatCost(Integer costCents) {
        if (costCents == null || costCents == 0) return "";
        // Wrap in quotes so the comma decimal separator doesn't break CSV column parsing.
        // Normalize non-breaking space (U+00A0) that newer JDK locales insert between
        // the currency symbol and the amount (e.g. "R$ 150,00" → "R$ 150,00").
        String formatted = COST_FORMAT.format(costCents / 100.0).replace(' ', ' ');
        return "\"" + formatted + "\"";
    }
}
