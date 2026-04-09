package com.brainbyte.easy_maintenance.billing.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ProrataCalculatorTest {

    private ProrataCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ProrataCalculator();
    }

    // -----------------------------------------------------------------------
    // Casos de borda: posição relativa de "now" ao período
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnNewPlanPriceWhenPeriodAlreadyEnded() {
        // now > periodEnd → cobra o plano novo inteiro
        Instant start = Instant.now().minus(Duration.ofDays(30));
        Instant end   = Instant.now().minus(Duration.ofDays(1));

        int result = calculator.calculateUpgradeCents(1000, 2000, start, end);

        assertEquals(2000, result, "Período encerrado deve retornar o preço do novo plano");
    }

    @Test
    void shouldChargeFullDifferenceWhenUpgradingBeforePeriodStarts() {
        // now < periodStart → tratado como periodStart → prorata = totalDuration → cobra diferença completa
        Instant start = Instant.now().plus(Duration.ofDays(10));
        Instant end   = start.plus(Duration.ofDays(30));

        int result = calculator.calculateUpgradeCents(1000, 2000, start, end);

        assertEquals(1000, result, "Antes do início do período, deve cobrar a diferença total entre os planos");
    }

    @Test
    void shouldReturnZeroWhenPeriodDurationIsZero() {
        // totalDuration = 0 → divisão por zero protegida
        Instant instant = Instant.now().plus(Duration.ofDays(5));

        int result = calculator.calculateUpgradeCents(1000, 2000, instant, instant);

        assertEquals(0, result);
    }

    // -----------------------------------------------------------------------
    // Upgrade / downgrade
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnZeroForDowngrade() {
        // current (2000) > new (1000) → resultado negativo → max(0)
        Instant start = Instant.now().plus(Duration.ofDays(5));
        Instant end   = start.plus(Duration.ofDays(30));

        int result = calculator.calculateUpgradeCents(2000, 1000, start, end);

        assertEquals(0, result, "Downgrade não deve cobrar valor adicional");
    }

    @Test
    void shouldReturnZeroForSamePricePlans() {
        Instant start = Instant.now().plus(Duration.ofDays(5));
        Instant end   = start.plus(Duration.ofDays(30));

        int result = calculator.calculateUpgradeCents(1500, 1500, start, end);

        assertEquals(0, result, "Planos com mesmo preço não devem gerar cobrança");
    }

    @Test
    void shouldReturnNewPlanPriceWhenCurrentPlanIsZero() {
        // Usuário sem plano sendo atualizado para plano pago — cobrar inteiro
        Instant start = Instant.now().plus(Duration.ofDays(5));
        Instant end   = start.plus(Duration.ofDays(30));

        int result = calculator.calculateUpgradeCents(0, 5000, start, end);

        assertEquals(5000, result, "Sem plano atual, deve cobrar o novo plano inteiro");
    }

    // -----------------------------------------------------------------------
    // Diferentes durações de mês (28, 30, 31 dias)
    // -----------------------------------------------------------------------

    @Test
    void shouldHandleCorrectly28DayMonth() {
        // Período futuro completo (28 dias) → cobra diferença inteira
        Instant start = Instant.now().plus(Duration.ofDays(2));
        Instant end   = start.plus(Duration.ofDays(28));

        int result = calculator.calculateUpgradeCents(2000, 4000, start, end);

        assertEquals(2000, result);
    }

    @Test
    void shouldHandleCorrectly30DayMonth() {
        Instant start = Instant.now().plus(Duration.ofDays(2));
        Instant end   = start.plus(Duration.ofDays(30));

        int result = calculator.calculateUpgradeCents(2000, 4000, start, end);

        assertEquals(2000, result);
    }

    @Test
    void shouldHandleCorrectly31DayMonth() {
        Instant start = Instant.now().plus(Duration.ofDays(2));
        Instant end   = start.plus(Duration.ofDays(31));

        int result = calculator.calculateUpgradeCents(2000, 4000, start, end);

        assertEquals(2000, result);
    }

    // -----------------------------------------------------------------------
    // Primeiro e último dia do ciclo
    // -----------------------------------------------------------------------

    @Test
    void shouldChargeNearFullAmountOnFirstDayOfCycle() {
        // Primeiro dia: now ≈ periodStart → remaining ≈ total → cobra quase a diferença inteira
        Instant start = Instant.now().minus(Duration.ofSeconds(1)); // poucos segundos atrás
        Instant end   = start.plus(Duration.ofDays(30));

        int result = calculator.calculateUpgradeCents(1000, 3000, start, end);

        // Com apenas 1 segundo consumido de 30 dias, o resultado deve ser próximo de 2000
        assertTrue(result >= 1998 && result <= 2000,
                "No primeiro dia, prorata deve ser próxima da diferença total, mas foi: " + result);
    }

    @Test
    void shouldChargeNearZeroOnLastDayOfCycle() {
        // Último dia: apenas ~10 segundos restantes de 30 dias
        Instant start = Instant.now().minus(Duration.ofDays(30));
        Instant end   = Instant.now().plus(Duration.ofSeconds(10));

        int result = calculator.calculateUpgradeCents(1000, 5000, start, end);

        // Apenas 10 segundos de 30 dias restantes → cobrança mínima
        assertTrue(result >= 0 && result <= 2,
                "No último dia, prorata deve ser próxima de zero, mas foi: " + result);
    }

    // -----------------------------------------------------------------------
    // Resultado nunca é negativo
    // -----------------------------------------------------------------------

    @Test
    void resultShouldNeverBeNegative() {
        Instant start = Instant.now().plus(Duration.ofDays(5));
        Instant end   = start.plus(Duration.ofDays(30));

        // Downgrade extremo
        int result = calculator.calculateUpgradeCents(99999, 1, start, end);

        assertTrue(result >= 0, "Resultado nunca deve ser negativo");
    }
}
