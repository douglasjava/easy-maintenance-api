package com.brainbyte.easy_maintenance.org_users.domain.enums;

import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import org.apache.commons.lang3.StringUtils;

public enum Plan {

    FREE,
    STARTER,
    BUSINESS,
    ENTERPRISE;

    public static Plan from(String value) {
        if (StringUtils.isBlank(value)) {
            throw new RuleException("Plan value cannot be null or empty");
        }

        try {
            return Plan.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RuleException(
                    "Invalid plan: '" + value + "'. Allowed values: FREE, STARTER, BUSINESS, ENTERPRISE"
            );
        }
    }

}
