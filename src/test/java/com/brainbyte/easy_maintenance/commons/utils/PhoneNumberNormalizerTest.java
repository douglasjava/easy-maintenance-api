package com.brainbyte.easy_maintenance.commons.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            // já em E.164 com o 9º dígito
            "+5531972139145, +5531972139145",
            // sem +, com 55 e 9º dígito
            "5531972139145, +5531972139145",
            // com máscara BR (celular, já com 9º dígito)
            "'(31) 97213-9145', +5531972139145",
            "'(31) 9 7213-9145', +5531972139145",
            // celular antigo sem o 9º dígito (BR 10 dígitos, DDD + 8 dígitos começando 6-9)
            "'(31) 7213-9145', +5531972139145",
            "3172139145, +5531972139145",
            // fixo (DDD + 8 dígitos começando 2-5) — nunca ganha o 9º dígito
            "'(31) 3213-9145', +553132139145",
            "3132139145, +553132139145",
            // fixo com 55 e máscara
            "'+55 (31) 3213-9145', +553132139145",
    })
    void normalizesValidBrazilianPhoneNumbers(String rawInput, String expectedE164) {
        assertThat(PhoneNumberNormalizer.toE164BR(rawInput))
                .contains(expectedE164);
    }

    @ParameterizedTest
    @CsvSource({
            "''",
            "'   '",
            "123",
            "abc",
            "12345678901234567890",
            "'(00) 12345-6789'", // DDD inválido (00)
    })
    void rejectsInvalidInput(String rawInput) {
        assertThat(PhoneNumberNormalizer.toE164BR(rawInput)).isEmpty();
    }

    @Test
    void rejectsNullInput() {
        assertThat(PhoneNumberNormalizer.toE164BR(null)).isEmpty();
    }
}
