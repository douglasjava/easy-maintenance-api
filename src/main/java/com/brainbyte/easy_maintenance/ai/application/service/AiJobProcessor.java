package com.brainbyte.easy_maintenance.ai.application.service;

import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapPreviewRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiAssistantRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiSuggestItemRequest;
import com.brainbyte.easy_maintenance.ai.domain.AiJob;
import com.brainbyte.easy_maintenance.ai.domain.enums.AiJobStatus;
import com.brainbyte.easy_maintenance.ai.infrastructure.persistence.AiJobRepository;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Processes AI jobs asynchronously on a dedicated thread pool.
 * Must be a separate Spring bean so that @Async goes through the proxy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiJobProcessor {

    private final AiJobRepository jobRepository;
    private final AiService aiService;
    private final AiBootstrapService bootstrapService;
    private final ObjectMapper objectMapper;

    @Async("aiJobExecutor")
    public void processAsync(String jobId, String orgCode) {
        TenantContext.set(orgCode);
        AiJob job = null;
        try {
            job = jobRepository.findById(jobId).orElseThrow(
                    () -> new IllegalStateException("Job not found after submit: " + jobId));

            job.setStatus(AiJobStatus.PROCESSING);
            job.setStartedAt(Instant.now());
            jobRepository.save(job);

            Object result = switch (job.getJobType()) {
                case ASSISTANT -> {
                    AiAssistantRequest req = objectMapper.readValue(job.getInputJson(), AiAssistantRequest.class);
                    yield aiService.assistant(req);
                }
                case SUGGEST_ITEM -> {
                    AiSuggestItemRequest req = objectMapper.readValue(job.getInputJson(), AiSuggestItemRequest.class);
                    yield aiService.suggestItem(req);
                }
                case BOOTSTRAP_PREVIEW -> {
                    AiBootstrapPreviewRequest req = objectMapper.readValue(job.getInputJson(), AiBootstrapPreviewRequest.class);
                    yield bootstrapService.preview(req);
                }
            };

            job.setResultJson(objectMapper.writeValueAsString(result));
            job.setStatus(AiJobStatus.DONE);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
            log.info("AI job {} completed — type={} org={}", jobId, job.getJobType(), orgCode);

        } catch (Exception e) {
            log.error("AI job {} failed — org={}: {}", jobId, orgCode, e.getMessage(), e);
            if (job != null) {
                job.setStatus(AiJobStatus.FAILED);
                job.setErrorMessage(e.getMessage());
                job.setCompletedAt(Instant.now());
                jobRepository.save(job);
            }
        } finally {
            TenantContext.clear();
        }
    }
}
