package com.brainbyte.easy_maintenance.jobs.infrastucture.web;

import com.brainbyte.easy_maintenance.jobs.service.TrialExpirationJobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/easy-maintenance/api/v1/run-jobs")
@Tag(name = "Executar Jobs", description = "Executar jobs")
public class JobController {

    private final TrialExpirationJobService trialExpirationJobService;

    @GetMapping("/execute-trial-expiration")
    public void executeTrialExpirationJobService() {

        trialExpirationJobService.processTrialsExpiringWithinDays(2);

    }

}
