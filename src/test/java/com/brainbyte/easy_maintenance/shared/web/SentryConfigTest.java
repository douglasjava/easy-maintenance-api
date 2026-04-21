package com.brainbyte.easy_maintenance.shared.web;

import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SentryConfigTest {

    private SentryOptions.BeforeSendCallback callback;

    @BeforeEach
    void setUp() {
        callback = new SentryConfig().sentryBeforeSendCallback();
    }

    // -----------------------------------------------------------------------
    // Header scrubbing
    // -----------------------------------------------------------------------

    @Test
    void shouldFilterAuthorizationHeader() {
        SentryEvent event = eventWithHeaders(Map.of(
                "Authorization", "Bearer eyJ...",
                "Content-Type", "application/json"
        ));

        SentryEvent result = callback.execute(event, null);

        assertThat(result.getRequest().getHeaders())
                .containsEntry("Authorization", "[Filtered]")
                .containsEntry("Content-Type", "application/json");
    }

    @Test
    void shouldFilterCookieHeaderCaseInsensitive() {
        SentryEvent event = eventWithHeaders(Map.of(
                "cookie", "accessToken=abc123; sessionId=xyz"
        ));

        SentryEvent result = callback.execute(event, null);

        assertThat(result.getRequest().getHeaders())
                .containsEntry("cookie", "[Filtered]");
    }

    @Test
    void shouldFilterAllSensitiveHeaders() {
        Map<String, String> headers = new HashMap<>();
        for (String header : SentryConfig.SENSITIVE_HEADERS) {
            headers.put(header, "sensitive-value");
        }
        headers.put("x-request-id", "abc-123");

        SentryEvent event = eventWithHeaders(headers);
        SentryEvent result = callback.execute(event, null);

        SentryConfig.SENSITIVE_HEADERS.forEach(h ->
                assertThat(result.getRequest().getHeaders())
                        .as("Header '%s' should be filtered", h)
                        .containsEntry(h, "[Filtered]")
        );
        assertThat(result.getRequest().getHeaders())
                .containsEntry("x-request-id", "abc-123");
    }

    @Test
    void shouldNotDropEventWhenRequestIsNull() {
        SentryEvent event = new SentryEvent();
        // no request set

        SentryEvent result = callback.execute(event, null);

        assertThat(result).isNotNull();
    }

    // -----------------------------------------------------------------------
    // Cookie scrubbing
    // -----------------------------------------------------------------------

    @Test
    void shouldReplaceCookieStringWithFiltered() {
        SentryEvent event = new SentryEvent();
        Request req = new Request();
        req.setCookies("accessToken=tok123; otherCookie=val");
        event.setRequest(req);

        SentryEvent result = callback.execute(event, null);

        assertThat(result.getRequest().getCookies()).isEqualTo("[Filtered]");
    }

    @Test
    void shouldSetCookiesToFilteredEvenWhenCookiesWereNull() {
        SentryEvent event = new SentryEvent();
        Request req = new Request();
        // cookies is null by default
        event.setRequest(req);

        SentryEvent result = callback.execute(event, null);

        assertThat(result.getRequest().getCookies()).isEqualTo("[Filtered]");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private SentryEvent eventWithHeaders(Map<String, String> headers) {
        SentryEvent event = new SentryEvent();
        Request req = new Request();
        req.setHeaders(new HashMap<>(headers));
        event.setRequest(req);
        return event;
    }
}
