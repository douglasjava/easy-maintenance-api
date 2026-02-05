package com.brainbyte.easy_maintenance.commons.validation;

public final class DocumentValidator {

    private DocumentValidator() {}

    /**
     * Remove todos os caracteres que não sejam dígitos (0-9).
     */
    public static String sanitize(String document) {
        if (document == null) return null;
        return document.replaceAll("[^0-9]", "");
    }
}
