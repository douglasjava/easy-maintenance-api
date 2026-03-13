package com.brainbyte.easy_maintenance.commons.helper;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.OffsetDateTime;

@UtilityClass
public class DateUtils {

    public static Instant parseEventDate(String dateStr) {
        try {
            return OffsetDateTime.parse(dateStr).toInstant();
        } catch (Exception e) {
            return Instant.now();
        }
    }

}
