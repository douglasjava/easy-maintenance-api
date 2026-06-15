package com.brainbyte.easy_maintenance.infrastructure.observability.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records last-success timestamps for critical scheduled jobs as Prometheus gauges.
 * Alert rule: (time() - easy_job_last_success_seconds{job="..."}) > 93600 (26h)
 */
@Component
public class JobHealthReporter {

    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> timestamps = new ConcurrentHashMap<>();

    public JobHealthReporter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void markSuccess(String jobName) {
        timestamps.computeIfAbsent(jobName, name -> {
            AtomicLong ts = new AtomicLong(Instant.now().getEpochSecond());
            meterRegistry.gauge(
                    "easy_job.last_success_seconds",
                    List.of(Tag.of("job", name)),
                    ts
            );
            return ts;
        }).set(Instant.now().getEpochSecond());
    }
}
