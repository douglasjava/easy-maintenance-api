package com.brainbyte.easy_maintenance.ai.domain;

import com.brainbyte.easy_maintenance.ai.domain.enums.AiJobStatus;
import com.brainbyte.easy_maintenance.ai.domain.enums.AiJobType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ai_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiJob {

    @Id
    private String id;

    @Column(name = "organization_code", nullable = false)
    private String organizationCode;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private AiJobType jobType;

    @Column(name = "input_json", nullable = false, columnDefinition = "TEXT")
    private String inputJson;

    @Column(name = "result_json", columnDefinition = "MEDIUMTEXT")
    private String resultJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AiJobStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
