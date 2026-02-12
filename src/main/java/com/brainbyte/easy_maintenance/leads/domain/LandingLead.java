package com.brainbyte.easy_maintenance.leads.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "landing_leads")
public class LandingLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String name;

    private String source;
    private String medium;
    private String campaign;

    private String referrer;

    @Column(name = "landing_path")
    private String landingPath;

    @Column(name = "utm_json", columnDefinition = "json")
    private String utmJson;

    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Builder.Default
    private String status = "NEW";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

}
