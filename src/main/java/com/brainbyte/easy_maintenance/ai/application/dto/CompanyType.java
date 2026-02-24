package com.brainbyte.easy_maintenance.ai.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CompanyType {
    CONDOMINIUM("CONDOMINIO"),
    HOSPITAL("HOSPITAL"),
    SCHOOL("ESCOLA"),
    INDUSTRY("INDUSTRIA"),
    OFFICE("ESCRITORIO"),
    OTHER("OUTROS");

    private final String dbValue;

}
