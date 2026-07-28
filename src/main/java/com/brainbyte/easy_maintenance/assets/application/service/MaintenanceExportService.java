package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.CrossOrgMaintenanceExportProjection;
import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceExportProjection;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceAttachment;
import com.brainbyte.easy_maintenance.assets.domain.rules.StatusCalculator;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotAuthorizedException;
import com.brainbyte.easy_maintenance.infrastructure.access.application.service.SubscriptionAccessService;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final UserRepository userRepository;
    private final MaintenanceAttachmentRepository attachmentRepository;

    public byte[] exportCsv(String orgCode, Long itemId, LocalDate startDate, LocalDate endDate,
                             String performedBy) {
        checkReportsFeature(orgCode);

        List<MaintenanceExportProjection> rows =
                maintenanceRepository.findForExport(orgCode, itemId, startDate, endDate,
                        (performedBy != null && !performedBy.isBlank()) ? performedBy : null);

        log.info("[Export] org={} itemId={} performedBy={} rows={}", orgCode, itemId, performedBy, rows.size());

        Map<Long, String> nameById = resolveUserNames(
                rows.stream().map(MaintenanceExportProjection::getCreatedBy).filter(Objects::nonNull).collect(Collectors.toSet()));

        return buildCsv(rows, nameById);
    }

    public byte[] exportExcelCrossOrg(Long userId, List<String> requestedOrgCodes,
                                       LocalDate startDate, LocalDate endDate,
                                       String type, String itemType) {
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

        String typeFilter = (type != null && !type.isBlank()) ? type : null;
        String itemTypeFilter = (itemType != null && !itemType.isBlank()) ? itemType : null;

        List<CrossOrgMaintenanceExportProjection> rows =
                maintenanceRepository.findForExportCrossOrg(authorizedOrgCodes, startDate, endDate,
                        typeFilter, itemTypeFilter);

        log.info("[CrossOrgExport] userId={} orgs={} type={} itemType={} rows={}",
                userId, authorizedOrgCodes, type, itemType, rows.size());

        Map<Long, String> nameById = resolveUserNames(
                rows.stream().map(CrossOrgMaintenanceExportProjection::getCreatedBy).filter(Objects::nonNull).collect(Collectors.toSet()));

        // TASK-147: contagem de anexos por manutenção resolvida numa única query em lote — mesmo
        // cuidado da TASK-142 (findByMaintenanceIdIn), essencial aqui porque um export pode ter até
        // 5000 linhas.
        Set<Long> maintenanceIds = rows.stream().map(CrossOrgMaintenanceExportProjection::getId).collect(Collectors.toSet());
        Map<Long, Long> attachmentCountByMaintenanceId = maintenanceIds.isEmpty()
                ? Map.of()
                : attachmentRepository.findByMaintenanceIdIn(maintenanceIds).stream()
                        .collect(Collectors.groupingBy(MaintenanceAttachment::getMaintenanceId, Collectors.counting()));

        return buildExcelCrossOrg(rows, orgNames, nameById, attachmentCountByMaintenanceId);
    }

    // TASK-147: CSV puro trocado por .xlsx real (Apache POI) — tipos nativos (número, data) em vez
    // de texto formatado, pra permitir cálculo direto no Excel sem conversão manual do usuário.
    private byte[] buildExcelCrossOrg(List<CrossOrgMaintenanceExportProjection> rows,
                                       Map<String, String> orgNames, Map<Long, String> nameById,
                                       Map<Long, Long> attachmentCountByMaintenanceId) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Manutenções");

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("R$ #,##0.00"));

            String[] headers = {
                    "ID", "Empresa", "Item", "Data da Manutenção", "Tipo", "Responsável",
                    "Custo (R$)", "Próxima Data", "Norma Aplicável", "Categoria", "Registrado por",
                    "Status do item", "Qtd. de evidências anexadas"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowIdx = 1;
            for (var row : rows) {
                Row excelRow = sheet.createRow(rowIdx++);
                excelRow.createCell(0).setCellValue(row.getId());
                excelRow.createCell(1).setCellValue(orgNames.getOrDefault(row.getOrgCode(), row.getOrgCode()));
                excelRow.createCell(2).setCellValue(row.getItemType());
                setDateCell(excelRow.createCell(3), row.getPerformedAt(), dateStyle);
                excelRow.createCell(4).setCellValue(row.getMaintenanceType());
                excelRow.createCell(5).setCellValue(row.getPerformedBy());
                setCostCell(excelRow.createCell(6), row.getCostCents(), moneyStyle);
                setDateCell(excelRow.createCell(7), row.getNextDueAt(), dateStyle);
                excelRow.createCell(8).setCellValue(row.getNormAuthority());
                excelRow.createCell(9).setCellValue(translateCategory(row.getItemCategory()));
                excelRow.createCell(10).setCellValue(resolvedName(row.getCreatedBy(), nameById));
                excelRow.createCell(11).setCellValue(translateStatus(StatusCalculator.calculate(row.getNextDueAt())));
                excelRow.createCell(12).setCellValue(attachmentCountByMaintenanceId.getOrDefault(row.getId(), 0L));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 1);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Erro ao gerar arquivo Excel do relatório", e);
        }
    }

    private void setDateCell(Cell cell, LocalDate date, CellStyle style) {
        if (date == null) return;
        cell.setCellValue(date);
        cell.setCellStyle(style);
    }

    private void setCostCell(Cell cell, Integer costCents, CellStyle style) {
        if (costCents == null) return;
        cell.setCellValue(costCents / 100.0);
        cell.setCellStyle(style);
    }

    private String translateStatus(com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus status) {
        return switch (status) {
            case OK -> "Em dia";
            case NEAR_DUE -> "Próximo do vencimento";
            case OVERDUE -> "Vencido";
        };
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

    private Map<Long, String> resolveUserNames(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
    }

    private String resolvedName(Long userId, Map<Long, String> nameById) {
        if (userId == null) return "—";
        return nameById.getOrDefault(userId, "—");
    }

    private byte[] buildCsv(List<MaintenanceExportProjection> rows, Map<Long, String> nameById) {
        var sb = new StringBuilder();
        sb.append('﻿'); // UTF-8 BOM — required for Excel on Windows to decode accents correctly
        sb.append("ID,Item,Data da Manutenção,Tipo,Responsável,Custo (R$),Próxima Data,Norma Aplicável,Categoria,Registrado por\n");

        for (var row : rows) {
            sb.append(row.getId()).append(",");
            sb.append(csv(row.getItemType())).append(",");
            sb.append(dateStr(row.getPerformedAt())).append(",");
            sb.append(csv(row.getMaintenanceType())).append(",");
            sb.append(csv(row.getPerformedBy())).append(",");
            sb.append(formatCost(row.getCostCents())).append(",");
            sb.append(dateStr(row.getNextDueAt())).append(",");
            sb.append(csv(row.getNormAuthority())).append(",");
            sb.append(csv(translateCategory(row.getItemCategory()))).append(",");
            sb.append(csv(resolvedName(row.getCreatedBy(), nameById))).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String translateCategory(String category) {
        if (category == null) return "";
        return switch (category.toUpperCase()) {
            case "REGULATORY" -> "Regulatório";
            case "OPERATIONAL" -> "Operacional";
            default -> category;
        };
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
