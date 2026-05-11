package com.brainbyte.easy_maintenance.ai.application.service;

import com.brainbyte.easy_maintenance.ai.application.dto.AiJobStatusResponse;
import com.brainbyte.easy_maintenance.ai.domain.AiJob;
import com.brainbyte.easy_maintenance.ai.domain.enums.AiJobStatus;
import com.brainbyte.easy_maintenance.ai.domain.enums.AiJobType;
import com.brainbyte.easy_maintenance.ai.infrastructure.persistence.AiJobRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.InternalErrorException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotAuthorizedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiJobService {

    private final AiJobRepository jobRepository;
    private final AiJobProcessor aiJobProcessor;
    private final AiCreditService aiCreditService;
    private final ObjectMapper objectMapper;

    public String submitJob(String orgCode, Long userId, AiJobType jobType, Object inputDto) {
        Objects.requireNonNull(userId, "userId is required for AI job submission");
        aiCreditService.validateHasCredits(userId);
        try {
            String inputJson = objectMapper.writeValueAsString(inputDto);
            AiJob job = AiJob.builder()
                    .id(UUID.randomUUID().toString())
                    .organizationCode(orgCode)
                    .userId(userId)
                    .jobType(jobType)
                    .inputJson(inputJson)
                    .status(AiJobStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();
            jobRepository.save(job);
            // processAsync is on a different bean — Spring proxy applies @Async correctly
            aiJobProcessor.processAsync(job.getId(), orgCode);
            log.info("AI job submitted — id={} type={} org={} userId={}", job.getId(), jobType, orgCode, userId);
            return job.getId();
        } catch (JsonProcessingException e) {
            throw new InternalErrorException("Falha ao serializar input do job de IA", e);
        }
    }

    public AiJobStatusResponse getJobStatus(String jobId, String orgCode) {
        AiJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job não encontrado: " + jobId));

        if (!orgCode.equals(job.getOrganizationCode())) {
            throw new NotAuthorizedException("Acesso negado ao job de IA: " + jobId);
        }

        Object result = null;
        if (job.getStatus() == AiJobStatus.DONE && job.getResultJson() != null) {
            try {
                result = objectMapper.readValue(job.getResultJson(), Object.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize result for AI job {}", jobId);
            }
        }

        return new AiJobStatusResponse(
                job.getId(),
                job.getStatus(),
                result,
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getCompletedAt()
        );
    }

    @Scheduled(fixedDelay = 3_600_000)
    @SchedulerLock(name = "cleanupExpiredAiJobs", lockAtMostFor = "PT5M")
    public void cleanupExpiredJobs() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        int deleted = jobRepository.deleteCompletedBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} expired AI jobs (completed before {})", deleted, cutoff);
        }
    }
}
