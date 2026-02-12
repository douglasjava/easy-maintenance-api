package com.brainbyte.easy_maintenance.leads.application.service;

import com.brainbyte.easy_maintenance.leads.application.dto.CreateLeadRequest;
import com.brainbyte.easy_maintenance.leads.application.dto.LeadResponse;
import com.brainbyte.easy_maintenance.leads.domain.LandingLead;
import com.brainbyte.easy_maintenance.leads.infrastructure.persistence.LandingLeadRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadService {

    private final LandingLeadRepository repository;

    @Transactional
    public LeadResponse createLead(CreateLeadRequest request, HttpServletRequest httpRequest) {
        log.info("Creating new lead with email: {}", request.email());

        LandingLead lead = LandingLead.builder()
                .email(request.email())
                .name(request.name())
                .source(request.source())
                .medium(request.medium())
                .campaign(request.campaign())
                .referrer(request.referrer())
                .landingPath(request.landingPath())
                .utmJson(request.utmJson())
                .ip(httpRequest.getRemoteAddr())
                .userAgent(httpRequest.getHeader("User-Agent"))
                .status("NEW")
                .build();

        LandingLead saved = repository.save(lead);

        return new LeadResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getName(),
                saved.getStatus(),
                saved.getCreatedAt()
        );
    }
}
