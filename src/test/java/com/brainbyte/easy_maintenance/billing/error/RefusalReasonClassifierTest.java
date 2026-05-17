package com.brainbyte.easy_maintenance.billing.error;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RefusalReasonClassifierTest {

    private MeterRegistry meterRegistry;
    private RefusalClassificationProperties properties;
    private RefusalReasonClassifier classifier;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new RefusalClassificationProperties();
        classifier = new RefusalReasonClassifier(meterRegistry, properties);
        classifier.init();
    }

    // -------------------------------------------------------------------------
    // Default bucket mappings
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} → TRANSIENT")
    @CsvSource({
            "BANK_NOT_RESPONDING",
            "TIMEOUT",
            "GATEWAY_ERROR",
            "PIX_EXPIRED",
            "PROCESSING_ERROR"
    })
    void defaultMapping_transientCodes(String code) {
        assertThat(classifier.classify(code)).isEqualTo(RefusalBucket.TRANSIENT);
    }

    @ParameterizedTest(name = "{0} → USER_ACTION")
    @CsvSource({
            "INSUFFICIENT_FUNDS",
            "INVALID_CARD",
            "EXPIRED_CARD",
            "DO_NOT_HONOR",
            "AUTHORIZATION_DENIED",
            "PIX_KEY_NOT_FOUND",
            "INVALID_CVV"
    })
    void defaultMapping_userActionCodes(String code) {
        assertThat(classifier.classify(code)).isEqualTo(RefusalBucket.USER_ACTION);
    }

    @ParameterizedTest(name = "{0} → HARD_FAIL")
    @CsvSource({
            "FRAUD_DETECTED",
            "CARD_STOLEN",
            "CARD_LOST",
            "SECURITY_VIOLATION"
    })
    void defaultMapping_hardFailCodes(String code) {
        assertThat(classifier.classify(code)).isEqualTo(RefusalBucket.HARD_FAIL);
    }

    @ParameterizedTest(name = "{0} → INFO")
    @CsvSource({
            "CHARGEBACK",
            "DISPUTE"
    })
    void defaultMapping_infoCodes(String code) {
        assertThat(classifier.classify(code)).isEqualTo(RefusalBucket.INFO);
    }

    @Test
    void unknownCode_returnsUnknown() {
        assertThat(classifier.classify("TOTALLY_UNKNOWN_CODE")).isEqualTo(RefusalBucket.UNKNOWN);
    }

    @Test
    void nullCode_returnsUnknown() {
        assertThat(classifier.classify(null)).isEqualTo(RefusalBucket.UNKNOWN);
    }

    @Test
    void codeIsCaseInsensitive() {
        assertThat(classifier.classify("insufficient_funds")).isEqualTo(RefusalBucket.USER_ACTION);
        assertThat(classifier.classify("Fraud_Detected")).isEqualTo(RefusalBucket.HARD_FAIL);
    }

    // -------------------------------------------------------------------------
    // External overrides via properties
    // -------------------------------------------------------------------------

    @Test
    void override_remapsExistingCode() {
        properties.getOverrides().put("TIMEOUT", "HARD_FAIL");
        classifier.init();

        assertThat(classifier.classify("TIMEOUT")).isEqualTo(RefusalBucket.HARD_FAIL);
    }

    @Test
    void override_addsNewCode() {
        properties.getOverrides().put("MY_CUSTOM_CODE", "INFO");
        classifier.init();

        assertThat(classifier.classify("MY_CUSTOM_CODE")).isEqualTo(RefusalBucket.INFO);
    }

    @Test
    void override_invalidBucketName_ignoredAndDefaultApplies() {
        properties.getOverrides().put("TIMEOUT", "NONSENSE_BUCKET");
        classifier.init();

        // default for TIMEOUT is TRANSIENT; invalid override should be ignored
        assertThat(classifier.classify("TIMEOUT")).isEqualTo(RefusalBucket.TRANSIENT);
    }

    // -------------------------------------------------------------------------
    // Micrometer counter
    // -------------------------------------------------------------------------

    @Test
    void classifyIncrementsCounter() {
        classifier.classify("INSUFFICIENT_FUNDS"); // USER_ACTION
        classifier.classify("INSUFFICIENT_FUNDS"); // USER_ACTION again
        classifier.classify("TIMEOUT");             // TRANSIENT

        double userActionCount = meterRegistry
                .counter(RefusalReasonClassifier.METRIC_NAME, "bucket", "USER_ACTION")
                .count();
        double transientCount = meterRegistry
                .counter(RefusalReasonClassifier.METRIC_NAME, "bucket", "TRANSIENT")
                .count();

        assertThat(userActionCount).isEqualTo(2.0);
        assertThat(transientCount).isEqualTo(1.0);
    }

    @Test
    void unknownCode_incrementsUnknownCounter() {
        classifier.classify(null);
        classifier.classify("WEIRD_CODE");

        double unknownCount = meterRegistry
                .counter(RefusalReasonClassifier.METRIC_NAME, "bucket", "UNKNOWN")
                .count();

        assertThat(unknownCount).isEqualTo(2.0);
    }
}
