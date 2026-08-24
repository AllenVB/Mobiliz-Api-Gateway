package com.mobiliz.ysbridge.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mobiliz.ysbridge.gateway.ExposedEnvelope;
import com.mobiliz.ysbridge.gateway.exception.InvalidPageRequestException;
import com.mobiliz.ysbridge.gateway.exception.InvalidQueryParameterException;
import com.mobiliz.ysbridge.gateway.exception.MalformedRequestBodyException;
import com.mobiliz.ysbridge.gateway.exception.MethodNotAllowedException;
import com.mobiliz.ysbridge.gateway.exception.MissingQueryParameterException;
import com.mobiliz.ysbridge.gateway.exception.MissingTokenException;
import com.mobiliz.ysbridge.gateway.exception.NoSuchRouteException;
import com.mobiliz.ysbridge.gateway.exception.PayloadTooLargeException;
import com.mobiliz.ysbridge.gateway.exception.UnsupportedMediaTypeException;
import com.mobiliz.ysbridge.legacy.LegacyProtocolException;
import com.mobiliz.ysbridge.legacy.LegacyUnreachableException;
import com.mobiliz.ysbridge.mapping.engine.TranslationException;
import com.mobiliz.ysbridge.mapping.engine.UnmappedFieldException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every failure into the same envelope a success comes back in.
 *
 * <p>Three rules shape one.
 *
 * <ol>
 *   <li>The caller gets a stable {@code code} to branch on, never a message to parse. This is the
 *       one thing the legacy API cannot do: its failures are Turkish free text.
 *   <li>The HTTP status is truthful even though the envelope repeats the outcome. The legacy API
 *       answers {@code 201 Created} to failures; a client should not have to open the body to learn
 *       that something went wrong.
 *   <li>Nothing internal leaks. Stack traces and upstream bodies are logged, never returned.
 * </ol>
 */
@RestControllerAdvice
public class BridgeExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BridgeExceptionHandler.class);

    /** The legacy {@code type} on every failure seen so far. Carrying it would add nothing. */
    private static final String UNINFORMATIVE_LEGACY_TYPE = "ERROR";

    // ------------------------------------------------------------------ routing

    @ExceptionHandler(NoSuchRouteException.class)
    ResponseEntity<JsonNode> handleNoSuchRoute(NoSuchRouteException e) {
        return failure(HttpStatus.NOT_FOUND, e.getMessage(), "NO_SUCH_ENDPOINT");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<JsonNode> handleNoResource(NoResourceFoundException e) {
        return failure(
                HttpStatus.NOT_FOUND,
                "nothing is served at " + e.getResourcePath(),
                "NO_SUCH_ENDPOINT");
    }

    @ExceptionHandler(MethodNotAllowedException.class)
    ResponseEntity<JsonNode> handleMethodNotAllowed(MethodNotAllowedException e) {
        ObjectNode body = ExposedEnvelope.failure(e.getMessage(), "METHOD_NOT_ALLOWED");
        ArrayNode allowed = body.putArray("allowed");
        e.allowed().forEach(method -> allowed.add(method.toString()));

        HttpHeaders headers = new HttpHeaders();
        headers.setAllow(new LinkedHashSet<>(e.allowed()));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers).body(body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<JsonNode> handleUnsupportedMethod(HttpRequestMethodNotSupportedException e) {
        return failure(HttpStatus.METHOD_NOT_ALLOWED, e.getMessage(), "METHOD_NOT_ALLOWED");
    }

    // --------------------------------------------------------------- the caller

    @ExceptionHandler(MissingTokenException.class)
    ResponseEntity<JsonNode> handleMissingToken(MissingTokenException e) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .headers(headers)
                .body(ExposedEnvelope.failure(e.getMessage(), "MISSING_TOKEN"));
    }

    @ExceptionHandler(MissingQueryParameterException.class)
    ResponseEntity<JsonNode> handleMissingParameter(MissingQueryParameterException e) {
        ObjectNode body = ExposedEnvelope.failure(e.getMessage(), "MISSING_QUERY_PARAMETER");
        body.put("parameter", e.parameter());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(InvalidPageRequestException.class)
    ResponseEntity<JsonNode> handleInvalidPageRequest(InvalidPageRequestException e) {
        ObjectNode body = ExposedEnvelope.failure(e.getMessage(), "INVALID_PAGE_REQUEST");
        body.put("parameter", e.parameter());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(InvalidQueryParameterException.class)
    ResponseEntity<JsonNode> handleInvalidQueryParameter(InvalidQueryParameterException e) {
        ObjectNode body = ExposedEnvelope.failure(e.getMessage(), "INVALID_QUERY_PARAMETER");
        body.put("parameter", e.parameter());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MalformedRequestBodyException.class)
    ResponseEntity<JsonNode> handleMalformedBody(MalformedRequestBodyException e) {
        return failure(HttpStatus.BAD_REQUEST, e.getMessage(), "MALFORMED_REQUEST_BODY");
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    ResponseEntity<JsonNode> handleUnsupportedMediaType(UnsupportedMediaTypeException e) {
        ObjectNode body = ExposedEnvelope.failure(e.getMessage(), "UNSUPPORTED_MEDIA_TYPE");
        body.put("supplied", e.supplied());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<JsonNode> handleNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        return failure(
                HttpStatus.NOT_ACCEPTABLE,
                "this API answers with application/json only",
                "NOT_ACCEPTABLE");
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    ResponseEntity<JsonNode> handlePayloadTooLarge(PayloadTooLargeException e) {
        ObjectNode body = ExposedEnvelope.failure(e.getMessage(), "PAYLOAD_TOO_LARGE");
        body.put("limitBytes", e.limitBytes());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    @ExceptionHandler(UnmappedFieldException.class)
    ResponseEntity<JsonNode> handleUnmappedField(UnmappedFieldException e) {
        ObjectNode body =
                ExposedEnvelope.failure(
                        "the field '%s' is not part of this endpoint's contract"
                                .formatted(e.path()),
                        "UNRECOGNISED_FIELD");
        body.put("field", e.path());
        return ResponseEntity.badRequest().body(body);
    }

    // -------------------------------------------------------------- the upstream

    @ExceptionHandler(LegacyErrorException.class)
    ResponseEntity<JsonNode> handleLegacyError(LegacyErrorException e) {
        // A legacy failure can carry data worth keeping; see LegacyErrorException. It goes in
        // `result`, the envelope's own slot for it, rather than a field invented alongside.
        ObjectNode body = ExposedEnvelope.failure(e.title(), e.code(), e.payload());

        if (e.legacyMessage() != null && !e.legacyMessage().isBlank()) {
            body.put(ExposedEnvelope.LEGACY_MESSAGE, e.legacyMessage());
        }
        if (e.legacyType() != null && !UNINFORMATIVE_LEGACY_TYPE.equals(e.legacyType())) {
            // The legacy `type` is dropped from the exposed envelope because it only ever
            // duplicates `success`. If it ever says something else, it is not thrown away.
            body.put("legacyType", e.legacyType());
        }
        if (!e.isRecognised()) {
            log.warn(
                    "unrecognised legacy error, add it to error-catalog.yaml: type={} message={}",
                    e.legacyType(),
                    e.legacyMessage());
        }
        return ResponseEntity.status(e.status()).body(body);
    }

    @ExceptionHandler(LegacyProtocolException.class)
    ResponseEntity<JsonNode> handleProtocolFailure(LegacyProtocolException e) {
        if (e.kind() == LegacyProtocolException.Kind.CHALLENGE) {
            // Not the legacy application's fault: the request never reached it.
            log.error(
                    "the legacy API was not reached — an upstream bot-protection challenge answered "
                            + "instead. The bridge host most likely needs a WAF allowlist entry.");
        } else {
            log.error("the legacy API returned something unusable: {}", e.getMessage());
        }
        return failure(HttpStatus.BAD_GATEWAY, e.getMessage(), e.kind().code());
    }

    @ExceptionHandler(LegacyUnreachableException.class)
    ResponseEntity<JsonNode> handleUnreachable(LegacyUnreachableException e) {
        boolean timedOut = isTimeout(e);
        log.error("legacy API unreachable: {}", e.getMessage());

        return failure(
                timedOut ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
                e.getMessage(),
                timedOut ? "UPSTREAM_TIMEOUT" : "UPSTREAM_UNREACHABLE");
    }

    @ExceptionHandler(TranslationException.class)
    ResponseEntity<JsonNode> handleTranslationFailure(TranslationException e) {
        log.error("translation failed, the mapping and the payload disagree: {}", e.getMessage());
        return failure(HttpStatus.BAD_GATEWAY, e.getMessage(), "MAPPING_MISMATCH");
    }

    // ------------------------------------------------------------- last resort

    /**
     * Anything not handled above.
     *
     * <p>Without this, an unexpected exception reaches the container's default error handling,
     * which is both inconsistent with every other response this API gives and liable to disclose
     * internals. The cause is logged in full; the caller is told only that something failed.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<JsonNode> handleUnexpected(Exception e) {
        log.error("unhandled exception while serving a request", e);
        return failure(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "the request could not be completed",
                "INTERNAL_ERROR");
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static ResponseEntity<JsonNode> failure(
            HttpStatus status, String message, String code) {
        return ResponseEntity.status(status).body(ExposedEnvelope.failure(message, code));
    }
}
