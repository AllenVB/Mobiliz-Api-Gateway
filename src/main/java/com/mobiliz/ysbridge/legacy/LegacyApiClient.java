package com.mobiliz.ysbridge.legacy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
    private final int maxResponseBodyBytes;

    public LegacyApiClient(RestClient restClient, long maxResponseBodyBytes) {
        this.restClient = restClient;
        this.maxResponseBodyBytes = (int) Math.min(maxResponseBodyBytes, Integer.MAX_VALUE - 1);
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

            // Read through exchange() rather than toEntity(String.class) so the body can be
            // bounded. The convenience method buffers whatever arrives, which makes the size
            // of this service's heap a function of what the legacy API decides to send.
            LegacyResponse response =
                    (body == null ? spec : spec.body(body))
                            .exchange(
                                    (request, clientResponse) ->
                                            new LegacyResponse(
                                                    clientResponse.getStatusCode().value(),
                                                    clientResponse.getHeaders().getContentType(),
                                                    readBounded(clientResponse.getBody())),
                                    // Never throw on status: the body is the only source of
                                    // truth, and the legacy API answers 201 to failures.
                                    false);

            log.debug(
                    "legacy {} {} answered {} ({} bytes)",
                    method,
                    pathTemplate,
                    response.status(),
                    response.body() == null ? 0 : response.body().length());

            return response;
        } catch (ResourceAccessException e) {
            // Connection refused, DNS failure, timeout — the request never got an answer.
            throw new LegacyUnreachableException(
                    "the legacy API could not be reached: " + describe(e.getMostSpecificCause()),
                    e);
        }
    }

    /**
     * Reads at most the configured bound, and refuses rather than truncating.
     *
     * <p>Truncating would be worse than refusing: a body cut mid-JSON parses as malformed, and
     * whoever reads that error goes looking for a bug in the legacy API's output rather than for a
     * response that outgrew the limit. The same reasoning gives it the {@code RESPONSE_TOO_LARGE}
     * kind rather than {@code MALFORMED_JSON}.
     */
    private String readBounded(InputStream stream) throws IOException {
        if (stream == null) {
            return null;
        }
        byte[] raw = stream.readNBytes(maxResponseBodyBytes + 1);
        if (raw.length > maxResponseBodyBytes) {
            throw new LegacyProtocolException(
                    LegacyProtocolException.Kind.RESPONSE_TOO_LARGE,
                    "the legacy API returned more than the %d byte limit this gateway reads"
                            .formatted(maxResponseBodyBytes));
        }
        return new String(raw, StandardCharsets.UTF_8);
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
