package com.mobiliz.ysbridge.legacy;

import org.springframework.http.MediaType;

/**
 * A raw response from the legacy API, before any interpretation.
 *
 * <p>The status is kept only for logging. The legacy API answers {@code 201} for reads and for
 * errors alike, so it never decides anything here.
 *
 * @param status HTTP status the legacy API returned
 * @param contentType declared content type, or {@code null}
 * @param body raw body, or {@code null}
 */
public record LegacyResponse(int status, MediaType contentType, String body) {}
