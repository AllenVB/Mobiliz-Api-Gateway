package com.mobiliz.ysbridge.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id, and echoes it back.
 *
 * <p>A bridge sits between two systems, so a report of "it failed" has to be traceable across both.
 * The caller's id is honoured when supplied so the trail starts upstream.
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    /** Bound so a caller cannot push an arbitrarily long value into every log line. */
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = sanitise(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String sanitise(String supplied) {
        if (supplied == null || supplied.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String trimmed = supplied.trim();
        String safe = trimmed.replaceAll("[^A-Za-z0-9._:-]", "");
        if (safe.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return safe.length() > MAX_LENGTH ? safe.substring(0, MAX_LENGTH) : safe;
    }
}
