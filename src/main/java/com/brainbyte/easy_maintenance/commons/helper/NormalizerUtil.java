package com.brainbyte.easy_maintenance.commons.helper;

import lombok.experimental.UtilityClass;

import java.text.Normalizer;

@UtilityClass
public class NormalizerUtil {

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toUpperCase()
                .trim();

        normalized = normalized.replaceAll("[^A-Z0-9 ]", "");

        return normalized;
    }

}
