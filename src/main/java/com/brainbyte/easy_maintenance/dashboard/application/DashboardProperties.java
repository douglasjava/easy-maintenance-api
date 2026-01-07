package com.brainbyte.easy_maintenance.dashboard.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "dashboard.ai")
public record DashboardProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("PT15M") String cacheTtl
) {
}
