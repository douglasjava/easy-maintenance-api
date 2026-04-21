package com.brainbyte.easy_maintenance.shared.web;

import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;

/**
 * Sentry configuration: registers a BeforeSendCallback that strips
 * sensitive data (tokens, cookies, auth headers) from every event
 * before it is transmitted to the Sentry server.
 *
 * PII scrubbing strategy:
 *  - HTTP Authorization / Cookie / Set-Cookie / X-Api-Key headers → "[Filtered]"
 *  - Cookie string (may contain accessToken) → removed entirely
 */
@Configuration
public class SentryConfig {

    static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "x-auth-token",
            "x-admin-token"
    );

    @Bean
    public SentryOptions.BeforeSendCallback sentryBeforeSendCallback() {
        return (event, hint) -> {
            scrubRequest(event.getRequest());
            return event;
        };
    }

    static void scrubRequest(Request request) {
        if (request == null) return;

        // Scrub sensitive headers
        Map<String, String> headers = request.getHeaders();
        if (headers != null) {
            headers.replaceAll((key, value) ->
                    SENSITIVE_HEADERS.contains(key.toLowerCase()) ? "[Filtered]" : value
            );
        }

        // Remove cookie string entirely — may contain HttpOnly accessToken name/value
        request.setCookies("[Filtered]");
    }
}
