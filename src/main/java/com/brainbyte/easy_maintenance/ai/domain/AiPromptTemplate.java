package com.brainbyte.easy_maintenance.ai.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_prompt_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiPromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_key", length = 80, nullable = false)
    private String templateKey;

    @Column(name = "company_type", length = 40, nullable = false)
    private String companyType;

    @Column(nullable = false)
    private Integer version;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "LONGTEXT")
    private String systemPrompt;

    @Column(name = "user_prompt", nullable = false, columnDefinition = "LONGTEXT")
    private String userPrompt;

    @Column(name = "output_schema_json", columnDefinition = "JSON")
    private String outputSchemaJson;

    @Column(name = "model_name", length = 80)
    private String modelName;

    @Column(precision = 3, scale = 2)
    private BigDecimal temperature;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
