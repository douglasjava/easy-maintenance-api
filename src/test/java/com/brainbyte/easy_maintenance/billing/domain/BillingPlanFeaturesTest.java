package com.brainbyte.easy_maintenance.billing.domain;

import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TASK-020: formal schema for features_json in billing_plans.
 *
 * Validates:
 * - correct deserialization of all 4 live plan JSON payloads
 * - backward compatibility: missing fields fall back to declared defaults
 * - forward compatibility: unknown fields are silently ignored
 * - null/blank JSON is handled gracefully by BillingPlanFeaturesHelper
 * - @JsonProperty names match the actual JSON keys in the DB seed
 */
class BillingPlanFeaturesTest {

    private ObjectMapper objectMapper;
    private BillingPlanFeaturesHelper helper;

    // JSON strings mirroring V21__seed_plans_billing.sql exactly
    private static final String FREE_JSON = """
            {
              "maxOrganizations": 1,
              "maxUsers": 1,
              "maxItems": 30,
              "aiEnabled": false,
              "aiMonthlyCredits": 0,
              "emailMonthlyLimit": 100,
              "reportsEnabled": false,
              "supportLevel": "COMMUNITY"
            }
            """;

    private static final String STARTER_JSON = """
            {
              "maxOrganizations": 2,
              "maxUsers": 5,
              "maxItems": 150,
              "aiEnabled": true,
              "aiMonthlyCredits": 20000,
              "emailMonthlyLimit": 1000,
              "reportsEnabled": true,
              "supportLevel": "EMAIL"
            }
            """;

    private static final String BUSINESS_JSON = """
            {
              "maxOrganizations": 5,
              "maxUsers": 15,
              "maxItems": 500,
              "aiEnabled": true,
              "aiMonthlyCredits": 60000,
              "emailMonthlyLimit": 3000,
              "reportsEnabled": true,
              "supportLevel": "PRIORITY_EMAIL"
            }
            """;

    private static final String ENTERPRISE_JSON = """
            {
              "maxOrganizations": 999,
              "maxUsers": 100,
              "maxItems": 5000,
              "aiEnabled": true,
              "aiMonthlyCredits": 200000,
              "emailMonthlyLimit": 10000,
              "reportsEnabled": true,
              "supportLevel": "DEDICATED"
            }
            """;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        helper = new BillingPlanFeaturesHelper(objectMapper);
    }

    // -----------------------------------------------------------------------
    // Plan deserialization — happy paths matching DB seed
    // -----------------------------------------------------------------------

    @Test
    void deserialize_freePlan() throws Exception {
        BillingPlanFeatures features = objectMapper.readValue(FREE_JSON, BillingPlanFeatures.class);

        assertThat(features.getMaxOrganizations()).isEqualTo(1);
        assertThat(features.getMaxUsers()).isEqualTo(1);
        assertThat(features.getMaxItems()).isEqualTo(30);
        assertThat(features.isAiEnabled()).isFalse();
        assertThat(features.getAiMonthlyCredits()).isZero();
        assertThat(features.getEmailMonthlyLimit()).isEqualTo(100);
        assertThat(features.isReportsEnabled()).isFalse();
        assertThat(features.getSupportLevel()).isEqualTo("COMMUNITY");
    }

    @Test
    void deserialize_starterPlan() throws Exception {
        BillingPlanFeatures features = objectMapper.readValue(STARTER_JSON, BillingPlanFeatures.class);

        assertThat(features.getMaxOrganizations()).isEqualTo(2);
        assertThat(features.getMaxUsers()).isEqualTo(5);
        assertThat(features.getMaxItems()).isEqualTo(150);
        assertThat(features.isAiEnabled()).isTrue();
        assertThat(features.getAiMonthlyCredits()).isEqualTo(20000);
        assertThat(features.getEmailMonthlyLimit()).isEqualTo(1000);
        assertThat(features.isReportsEnabled()).isTrue();
        assertThat(features.getSupportLevel()).isEqualTo("EMAIL");
    }

    @Test
    void deserialize_businessPlan() throws Exception {
        BillingPlanFeatures features = objectMapper.readValue(BUSINESS_JSON, BillingPlanFeatures.class);

        assertThat(features.getMaxOrganizations()).isEqualTo(5);
        assertThat(features.getMaxUsers()).isEqualTo(15);
        assertThat(features.getMaxItems()).isEqualTo(500);
        assertThat(features.isAiEnabled()).isTrue();
        assertThat(features.getAiMonthlyCredits()).isEqualTo(60000);
        assertThat(features.getEmailMonthlyLimit()).isEqualTo(3000);
        assertThat(features.isReportsEnabled()).isTrue();
        assertThat(features.getSupportLevel()).isEqualTo("PRIORITY_EMAIL");
    }

    @Test
    void deserialize_enterprisePlan() throws Exception {
        BillingPlanFeatures features = objectMapper.readValue(ENTERPRISE_JSON, BillingPlanFeatures.class);

        assertThat(features.getMaxOrganizations()).isEqualTo(999);
        assertThat(features.getMaxUsers()).isEqualTo(100);
        assertThat(features.getMaxItems()).isEqualTo(5000);
        assertThat(features.isAiEnabled()).isTrue();
        assertThat(features.getAiMonthlyCredits()).isEqualTo(200000);
        assertThat(features.getEmailMonthlyLimit()).isEqualTo(10000);
        assertThat(features.isReportsEnabled()).isTrue();
        assertThat(features.getSupportLevel()).isEqualTo("DEDICATED");
    }

    // -----------------------------------------------------------------------
    // Backward compatibility — missing fields fall back to defaults
    // -----------------------------------------------------------------------

    @Test
    void backwardCompat_missingSupportLevelDefaultsToCommunity() throws Exception {
        String json = """
                {
                  "maxOrganizations": 1,
                  "maxUsers": 1,
                  "maxItems": 30
                }
                """;

        BillingPlanFeatures features = objectMapper.readValue(json, BillingPlanFeatures.class);

        assertThat(features.getSupportLevel()).isEqualTo("COMMUNITY");
    }

    @Test
    void backwardCompat_missingNumericFieldsDefaultToDeclairedValues() throws Exception {
        // Simulates an old row that only has partial data
        String json = "{}";

        BillingPlanFeatures features = objectMapper.readValue(json, BillingPlanFeatures.class);

        assertThat(features.getMaxOrganizations()).isEqualTo(1);
        assertThat(features.getMaxUsers()).isEqualTo(1);
        assertThat(features.getMaxItems()).isEqualTo(30);
        assertThat(features.getEmailMonthlyLimit()).isEqualTo(100);
        assertThat(features.getSupportLevel()).isEqualTo("COMMUNITY");
    }

    @Test
    void backwardCompat_missingBooleanFieldsDefaultToFalse() throws Exception {
        String json = "{}";

        BillingPlanFeatures features = objectMapper.readValue(json, BillingPlanFeatures.class);

        assertThat(features.isAiEnabled()).isFalse();
        assertThat(features.isReportsEnabled()).isFalse();
        assertThat(features.getAiMonthlyCredits()).isZero();
    }

    // -----------------------------------------------------------------------
    // Forward compatibility — unknown fields are ignored
    // -----------------------------------------------------------------------

    @Test
    void forwardCompat_unknownFieldsAreIgnored() throws Exception {
        String json = """
                {
                  "maxItems": 50,
                  "newFutureFeature": true,
                  "anotherUnknownKey": 42
                }
                """;

        // Must not throw DeserializationException
        BillingPlanFeatures features = objectMapper.readValue(json, BillingPlanFeatures.class);

        assertThat(features.getMaxItems()).isEqualTo(50);
    }

    // -----------------------------------------------------------------------
    // BillingPlanFeaturesHelper — null / blank / malformed safety
    // -----------------------------------------------------------------------

    @Test
    void helper_nullPlanReturnsDefaults() {
        BillingPlanFeatures features = helper.parse(null);

        assertThat(features).isNotNull();
        assertThat(features.getSupportLevel()).isEqualTo("COMMUNITY");
        assertThat(features.getMaxOrganizations()).isEqualTo(1);
    }

    @Test
    void helper_nullFeaturesJsonReturnsDefaults() {
        BillingPlan plan = BillingPlan.builder().code("TEST").featuresJson(null).build();

        BillingPlanFeatures features = helper.parse(plan);

        assertThat(features).isNotNull();
        assertThat(features.getMaxItems()).isEqualTo(30);
    }

    @Test
    void helper_blankFeaturesJsonReturnsDefaults() {
        BillingPlan plan = BillingPlan.builder().code("TEST").featuresJson("   ").build();

        BillingPlanFeatures features = helper.parse(plan);

        assertThat(features).isNotNull();
        assertThat(features.getEmailMonthlyLimit()).isEqualTo(100);
    }

    @Test
    void helper_malformedJsonReturnsDefaults() {
        BillingPlan plan = BillingPlan.builder().code("TEST").featuresJson("not-valid-json").build();

        // Must not throw — helper catches parse errors
        BillingPlanFeatures features = helper.parse(plan);

        assertThat(features).isNotNull();
        assertThat(features.getSupportLevel()).isEqualTo("COMMUNITY");
    }
}
