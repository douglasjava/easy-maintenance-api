package com.brainbyte.easy_maintenance.infrastructure.observability.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class BusinessMetricsService {

    private final MeterRegistry meterRegistry;
    private static final String PREFIX = "easy_";

    public void counter(String name, String... tags) {
        meterRegistry.counter(PREFIX + name, tags).increment();
    }

    public void timer(String name, Runnable runnable, String... tags) {
        Timer timer = meterRegistry.timer(PREFIX + name, tags);
        timer.record(runnable);
    }

    public <T> T timer(String name, Supplier<T> supplier, String... tags) {
        Timer timer = meterRegistry.timer(PREFIX + name, tags);
        return timer.record(supplier);
    }

    public void gauge(String name, Number value, String... tags) {
        meterRegistry.gauge(PREFIX + name, Arrays.stream(tags).map(t -> {
            String[] parts = t.split("="); // Fallback simple logic if needed
            return Tag.of(parts[0], parts.length > 1 ? parts[1] : "");
        }).toList(), value);
    }
    
    // Helper to register dynamic gauges if needed, but Micrometer gauges should be registered once.
    // Usually we use a reference to an object that holds the value.
}
