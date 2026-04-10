package com.brainbyte.easy_maintenance.assets.application.dto;

import java.time.LocalDate;

/**
 * Native query projection for the maintenance CSV export.
 * Column aliases in the query must match getter names (without "get").
 */
public interface MaintenanceExportProjection {
    Long getId();
    String getItemType();
    LocalDate getPerformedAt();
    String getMaintenanceType();
    String getPerformedBy();
    Integer getCostCents();
    LocalDate getNextDueAt();
    String getNormAuthority();
}
