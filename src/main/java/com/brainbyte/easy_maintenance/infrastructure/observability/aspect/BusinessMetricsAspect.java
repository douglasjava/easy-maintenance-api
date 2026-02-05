package com.brainbyte.easy_maintenance.infrastructure.observability.aspect;

import com.brainbyte.easy_maintenance.infrastructure.observability.annotation.CountedBusiness;
import com.brainbyte.easy_maintenance.infrastructure.observability.annotation.TimedBusiness;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;


@Aspect
@Component
@RequiredArgsConstructor
public class BusinessMetricsAspect {

    private final MeterRegistry meterRegistry;
    private static final String PREFIX = "easy_";

    @Around("@annotation(countedBusiness)")
    public Object countBusinessMetric(ProceedingJoinPoint joinPoint, CountedBusiness countedBusiness) throws Throwable {
        String metricName = countedBusiness.value();
        if (metricName.isEmpty()) {
            metricName = joinPoint.getSignature().getName();
        }

        try {
            return joinPoint.proceed();
        } finally {
            meterRegistry.counter(PREFIX + metricName, countedBusiness.tags()).increment();
        }
    }

    @Around("@annotation(timedBusiness)")
    public Object timeBusinessMetric(ProceedingJoinPoint joinPoint, TimedBusiness timedBusiness) throws Throwable {
        String metricName = timedBusiness.value();
        if (metricName.isEmpty()) {
            metricName = joinPoint.getSignature().getName();
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return joinPoint.proceed();
        } finally {
            sample.stop(meterRegistry.timer(PREFIX + metricName, timedBusiness.tags()));
        }
    }
}
