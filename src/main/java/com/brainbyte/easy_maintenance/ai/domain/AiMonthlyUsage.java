package com.brainbyte.easy_maintenance.ai.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ai_usage_monthly")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMonthlyUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "usage_month", nullable = false, length = 7)
    private String usageMonth;

    @Column(name = "credits_used", nullable = false)
    private int creditsUsed;
}
