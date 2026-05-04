package com.brainbyte.easy_maintenance.ai.application.service;

import com.brainbyte.easy_maintenance.ai.application.dto.AiAssistantRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiJobStatusResponse;
import com.brainbyte.easy_maintenance.ai.domain.AiJob;
import com.brainbyte.easy_maintenance.ai.domain.enums.AiJobStatus;
import com.brainbyte.easy_maintenance.ai.domain.enums.AiJobType;
import com.brainbyte.easy_maintenance.ai.infrastructure.persistence.AiJobRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotAuthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiJobServiceTest {

    @Mock
    private AiJobRepository jobRepository;

    @Mock
    private AiJobProcessor aiJobProcessor;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AiJobService service;

    @Test
    void shouldSubmitJob_saveRecord_andTriggerAsyncProcessing() throws Exception {
        AiAssistantRequest req = AiAssistantRequest.builder().question("Quais itens vencem?").build();
        when(objectMapper.writeValueAsString(req)).thenReturn("{\"question\":\"Quais itens vencem?\"}");
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String jobId = service.submitJob("org-A", AiJobType.ASSISTANT, req);

        assertThat(jobId).isNotBlank();
        ArgumentCaptor<AiJob> captor = ArgumentCaptor.forClass(AiJob.class);
        verify(jobRepository).save(captor.capture());
        AiJob saved = captor.getValue();
        assertThat(saved.getOrganizationCode()).isEqualTo("org-A");
        assertThat(saved.getJobType()).isEqualTo(AiJobType.ASSISTANT);
        assertThat(saved.getStatus()).isEqualTo(AiJobStatus.PENDING);
        verify(aiJobProcessor).processAsync(jobId, "org-A");
    }

    @Test
    void shouldReturnJobStatus_withResult_whenDone() throws Exception {
        String resultJson = "{\"answer\":\"resposta\"}";
        AiJob job = AiJob.builder()
                .id("job-1")
                .organizationCode("org-A")
                .jobType(AiJobType.ASSISTANT)
                .status(AiJobStatus.DONE)
                .resultJson(resultJson)
                .createdAt(Instant.now())
                .completedAt(Instant.now())
                .build();
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(objectMapper.readValue(resultJson, Object.class)).thenReturn(java.util.Map.of("answer", "resposta"));

        AiJobStatusResponse response = service.getJobStatus("job-1", "org-A");

        assertThat(response.status()).isEqualTo(AiJobStatus.DONE);
        assertThat(response.result()).isNotNull();
        assertThat(response.error()).isNull();
    }

    @Test
    void shouldReturnPendingStatus_withoutResult() {
        AiJob job = AiJob.builder()
                .id("job-2")
                .organizationCode("org-A")
                .jobType(AiJobType.SUGGEST_ITEM)
                .status(AiJobStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        when(jobRepository.findById("job-2")).thenReturn(Optional.of(job));

        AiJobStatusResponse response = service.getJobStatus("job-2", "org-A");

        assertThat(response.status()).isEqualTo(AiJobStatus.PENDING);
        assertThat(response.result()).isNull();
        verifyNoMoreInteractions(objectMapper);
    }

    @Test
    void shouldThrowNotFoundException_whenJobDoesNotExist() {
        when(jobRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJobStatus("missing", "org-A"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldThrowNotAuthorizedException_whenJobBelongsToDifferentOrg() {
        AiJob job = AiJob.builder()
                .id("job-3")
                .organizationCode("org-B")
                .jobType(AiJobType.ASSISTANT)
                .status(AiJobStatus.DONE)
                .createdAt(Instant.now())
                .build();
        when(jobRepository.findById("job-3")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.getJobStatus("job-3", "org-A"))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void shouldReturnFailedStatus_withErrorMessage() {
        AiJob job = AiJob.builder()
                .id("job-4")
                .organizationCode("org-A")
                .jobType(AiJobType.BOOTSTRAP_PREVIEW)
                .status(AiJobStatus.FAILED)
                .errorMessage("OpenAI timeout")
                .createdAt(Instant.now())
                .completedAt(Instant.now())
                .build();
        when(jobRepository.findById("job-4")).thenReturn(Optional.of(job));

        AiJobStatusResponse response = service.getJobStatus("job-4", "org-A");

        assertThat(response.status()).isEqualTo(AiJobStatus.FAILED);
        assertThat(response.error()).isEqualTo("OpenAI timeout");
        assertThat(response.result()).isNull();
    }
}
