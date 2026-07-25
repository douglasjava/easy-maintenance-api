package com.brainbyte.easy_maintenance.billing.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Typed representation of the {@code features_json} column in {@code billing_plans}.
 *
 * <h3>Schema</h3>
 * <pre>
 * {
 *   "maxOrganizations"    : int     — organizations the tenant may manage        (default: 1)
 *   "maxUsers"            : int     — max active users per organization           (default: 1)
 *   "maxItems"            : int     — max maintenance items per organization      (default: 30)
 *   "aiEnabled"           : boolean — AI features available                       (default: false)
 *   "aiMonthlyCredits"    : int     — monthly AI token/credit budget              (default: 0)
 *   "emailMonthlyLimit"   : int     — max outbound e-mails per calendar month     (default: 100)
 *   "whatsappMonthlyLimit": int     — max outbound WhatsApp msgs per month        (default: 30)
 *   "reportsEnabled"      : boolean — PDF/Excel report export available           (default: false)
 *   "supportLevel"        : string  — COMMUNITY | EMAIL | PRIORITY_EMAIL | DEDICATED (default: "COMMUNITY")
 *   "maxFileSizeMb"       : int     — max size per uploaded file in MB            (default: 5)
 *   "maxMonthlyUploadsMb" : int     — monthly upload quota per org in MB          (default: 500)
 * }
 * </pre>
 *
 * <p>Unknown fields are silently ignored for forward compatibility ({@code @JsonIgnoreProperties}).
 * Missing fields fall back to the declared defaults, ensuring backward compatibility
 * when a new field is added to the schema without updating existing plan rows.
 *
 * <p>Parse instances via {@link com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper}
 * which provides null-safe, error-tolerant deserialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillingPlanFeatures {

    /** Number of organizations the tenant may manage concurrently. */
    @JsonProperty("maxOrganizations")
    @Builder.Default
    private int maxOrganizations = 1;

    /** Maximum number of active users per organization. */
    @JsonProperty("maxUsers")
    @Builder.Default
    private int maxUsers = 1;

    /** Maximum number of maintenance items tracked per organization. */
    @JsonProperty("maxItems")
    @Builder.Default
    private int maxItems = 30;

    /** Whether AI-assisted maintenance features are enabled on this plan. */
    @JsonProperty("aiEnabled")
    private boolean aiEnabled;

    /** Monthly AI token/credit budget. Zero disables AI regardless of {@code aiEnabled}. */
    @JsonProperty("aiMonthlyCredits")
    private int aiMonthlyCredits;

    /** Maximum number of outbound e-mails per calendar month. */
    @JsonProperty("emailMonthlyLimit")
    @Builder.Default
    private int emailMonthlyLimit = 100;

    /** Maximum number of outbound WhatsApp messages per calendar month — custo direto por mensagem,
     * limite mais conservador que o de e-mail por padrão. */
    @JsonProperty("whatsappMonthlyLimit")
    @Builder.Default
    private int whatsappMonthlyLimit = 30;

    /** Whether PDF / Excel report export is available on this plan. */
    @JsonProperty("reportsEnabled")
    private boolean reportsEnabled;

    /**
     * Support tier for this plan.
     * <ul>
     *   <li>{@code COMMUNITY} — community forum only</li>
     *   <li>{@code EMAIL} — e-mail support</li>
     *   <li>{@code PRIORITY_EMAIL} — priority e-mail support</li>
     *   <li>{@code DEDICATED} — dedicated account manager</li>
     * </ul>
     */
    @JsonProperty("supportLevel")
    @Builder.Default
    private String supportLevel = "COMMUNITY";

    /** Maximum file size per individual upload, in megabytes. */
    @JsonProperty("maxFileSizeMb")
    @Builder.Default
    private int maxFileSizeMb = 5;

    /** Monthly upload quota per organization, in megabytes. Enforced at presigned-URL generation time. */
    @JsonProperty("maxMonthlyUploadsMb")
    @Builder.Default
    private int maxMonthlyUploadsMb = 500;
}
