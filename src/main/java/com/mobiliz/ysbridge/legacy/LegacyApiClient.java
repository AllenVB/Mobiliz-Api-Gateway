package com.mobiliz.ysbridge.legacy;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Sends requests to the legacy API and returns whatever came back, uninterpreted.
 *
 * <p>Deliberately does not treat a 4xx or 5xx as a failure: the legacy API's status carries no
 * information, and its error detail lives in the body. Everything is handed to {@link
 * EnvelopeParser} to make sense of.
 */
public class LegacyApiClient {

    private static final Logger log = LoggerFactory.getLogger(LegacyApiClient.class);

    private final RestClient restClient;

    public LegacyApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * @param method HTTP method
     * @param pathTemplate legacy URI template, e.g. {@code /yetkiliservis/servisdetay/{detailId}}
     * @param pathVariables values for the template's variables
     * @param headers headers to send; already filtered to what may leave this service
     * @param body JSON body, or {@code null} for a request without one
     * @throws LegacyUnreachableException if the legacy API could not be reached at all
     */
    public LegacyResponse exchange(
            HttpMethod method,
            String pathTemplate,
            Map<String, String> pathVariables,
            HttpHeaders headers,
            String body) {
        try {
            RestClient.RequestBodySpec spec =
                    restClient
                            .method(method)
                            .uri(uriBuilder -> uriBuilder.path(pathTemplate).build(pathVariables))
                            .headers(target -> target.addAll(headers));

            ResponseEntity<String> response =
                    (body == null ? spec : spec.body(body))
                            .retrieve()
                            // Never throw on status: the body is the only source of truth.
                            .onStatus(status -> true, (request, ignored) -> {})
                            .toEntity(String.class);

            log.debug(
                    "legacy {} {} answered {} ({} bytes)",
                    method,
                    pathTemplate,
                    response.getStatusCode().value(),
                    response.getBody() == null ? 0 : response.getBody().length());

            return new LegacyResponse(
                    response.getStatusCode().value(),
                    response.getHeaders().getContentType(),
                    response.getBody());
        } catch (ResourceAccessException e) {
            // Connection refused, DNS failure, timeout — the request never got an answer.
            throw new LegacyUnreachableException(
                    "the legacy API could not be reached: " + describe(e.getMostSpecificCause()),
                    e);
        }
    }

    /**
     * Some connection failures carry no message at all — a bare {@code ConnectException} is common
     * — and "could not be reached: null" tells an operator nothing. Fall back to the exception
     * type, which at least names what went wrong.
     */
    private static String describe(Throwable cause) {
        if (cause == null) {
            return "no further detail available";
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
