package com.brainbyte.easy_maintenance.commons.utils;

import lombok.experimental.UtilityClass;

import java.util.Optional;
import java.util.regex.Pattern;

@UtilityClass
public class PhoneNumberNormalizer {

    private static final Pattern NON_DIGITS = Pattern.compile("[^0-9]");
    private static final int DDD_MIN = 11;
    private static final int DDD_MAX = 99;

    /**
     * Normaliza um telefone brasileiro (com ou sem máscara, com ou sem +55, com ou sem o 9º
     * dígito do celular) para E.164 (+55DDXXXXXXXXX ou +55DDXXXXXXXX para fixo).
     * Retorna Optional vazio se a entrada não puder ser normalizada para um número BR válido.
     */
    public static Optional<String> toE164BR(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return Optional.empty();
        }

        String digits = NON_DIGITS.matcher(rawInput).replaceAll("");

        // Remove o código do país (55) se já vier incluído — só quando sobrarem dígitos
        // suficientes para DDD + número, evitando remover um DDD que comece com 55 por coincidência.
        if (digits.startsWith("55") && digits.length() > 11) {
            digits = digits.substring(2);
        }

        if (digits.length() == 10) {
            String ddd = digits.substring(0, 2);
            String number = digits.substring(2);
            if (isLikelyMobileMissingNinthDigit(number)) {
                digits = ddd + "9" + number;
            }
        }

        if (digits.length() != 10 && digits.length() != 11) {
            return Optional.empty();
        }

        int ddd = Integer.parseInt(digits.substring(0, 2));
        if (ddd < DDD_MIN || ddd > DDD_MAX) {
            return Optional.empty();
        }

        return Optional.of("+55" + digits);
    }

    // Números de celular antigos (8 dígitos, sem o 9º dígito) historicamente começavam com
    // 6-9; fixos começam com 2-5. Heurística aceita — não existe forma determinística de
    // diferenciar sem consultar a operadora.
    private static boolean isLikelyMobileMissingNinthDigit(String eightDigitNumber) {
        char firstDigit = eightDigitNumber.charAt(0);
        return firstDigit >= '6' && firstDigit <= '9';
    }
}
