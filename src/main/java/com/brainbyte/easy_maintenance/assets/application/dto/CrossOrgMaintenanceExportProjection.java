package com.brainbyte.easy_maintenance.assets.application.dto;

import java.time.LocalDate;

public interface CrossOrgMaintenanceExportProjection {
    Long getId();
    String getOrgCode();
    String getItemType();
    LocalDate getPerformedAt();
    String getMaintenanceType();
    String getPerformedBy();
    Integer getCostCents();
    LocalDate getNextDueAt();
    String getNormAuthority();
    String getItemCategory();
    Long getCreatedBy();
}
