package com.mobiliz.ysbridge.error;

import com.mobiliz.ysbridge.gateway.exception.InvalidPageRequestException;
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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every failure into an RFC 7807 problem document.
 *
 * <p>Three rules shape what goes in one.
 *
 * <ol>
 *   <li>The caller gets a stable {@code code} to branch on, never a message to parse.
 *   <li>Nothing is said twice. {@code detail} is omitted where it would only repeat {@code title},
 *       and the legacy {@code type} is carried only when it says something the status does not
 *       already say — on a failure it is always {@code "ERROR"}, which is exactly what a 4xx
 *       already means.
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
    ProblemDetail handleNoSuchRoute(NoSuchRouteException e) {
        return problem(
                HttpStatus.NOT_FOUND, "No such endpoint", e.getMessage(), "NO_SUCH_ENDPOINT");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResource(NoResourceFoundException e) {
        return problem(
                HttpStatus.NOT_FOUND,
                "No such endpoint",
                "nothing is served at " + e.getResourcePath(),
                "NO_SUCH_ENDPOINT");
    }

    @ExceptionHandler(MethodNotAllowedException.class)
    ResponseEntity<ProblemDetail> handleMethodNotAllowed(MethodNotAllowedException e) {
        ProblemDetail problem =
                problem(
                        HttpStatus.METHOD_NOT_ALLOWED,
                        "Method not allowed",
                        e.getMessage(),
                        "METHOD_NOT_ALLOWED");
        problem.setProperty("allowed", e.allowed().stream().map(Object::toString).toList());

        HttpHeaders headers = new HttpHeaders();
        headers.setAllow(new LinkedHashSet<>(e.allowed()));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers).body(problem);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleUnsupportedMethod(HttpRequestMethodNotSupportedException e) {
        return problem(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Method not allowed",
                e.getMessage(),
                "METHOD_NOT_ALLOWED");
    }

    // --------------------------------------------------------------- the caller

    @ExceptionHandler(MissingTokenException.class)
    ResponseEntity<ProblemDetail> handleMissingToken(MissingTokenException e) {
        ProblemDetail problem =
                problem(
                        HttpStatus.UNAUTHORIZED,
                        "Authentication required",
                        e.getMessage(),
                        "MISSING_TOKEN");

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).headers(headers).body(problem);
    }

    @ExceptionHandler(MissingQueryParameterException.class)
    ProblemDetail handleMissingParameter(MissingQueryParameterException e) {
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "Missing query parameter",
                        e.getMessage(),
                        "MISSING_QUERY_PARAMETER");
        problem.setProperty("parameter", e.parameter());
        return problem;
    }

    @ExceptionHandler(InvalidPageRequestException.class)
    ProblemDetail handleInvalidPageRequest(InvalidPageRequestException e) {
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "Invalid paging parameter",
                        e.getMessage(),
                        "INVALID_PAGE_REQUEST");
        problem.setProperty("parameter", e.parameter());
        return problem;
    }

    @ExceptionHandler(MalformedRequestBodyException.class)
    ProblemDetail handleMalformedBody(MalformedRequestBodyException e) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Malformed request body",
                e.getMessage(),
                "MALFORMED_REQUEST_BODY");
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    ProblemDetail handleUnsupportedMediaType(UnsupportedMediaTypeException e) {
        ProblemDetail problem =
                problem(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Unsupported media type",
                        e.getMessage(),
                        "UNSUPPORTED_MEDIA_TYPE");
        problem.setProperty("supplied", e.supplied());
        return problem;
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ProblemDetail handleNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        return problem(
                HttpStatus.NOT_ACCEPTABLE,
                "Unsupported response format",
                "this API answers with application/json only",
                "NOT_ACCEPTABLE");
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    ProblemDetail handlePayloadTooLarge(PayloadTooLargeException e) {
        ProblemDetail problem =
                problem(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "Request body too large",
                        e.getMessage(),
                        "PAYLOAD_TOO_LARGE");
        problem.setProperty("limitBytes", e.limitBytes());
        return problem;
    }

    @ExceptionHandler(UnmappedFieldException.class)
    ProblemDetail handleUnmappedField(UnmappedFieldException e) {
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "Unrecognised field",
                        "the field '%s' is not part of this endpoint's contract"
                                .formatted(e.path()),
                        "UNRECOGNISED_FIELD");
        problem.setProperty("field", e.path());
        return problem;
    }

    // -------------------------------------------------------------- the upstream

    @ExceptionHandler(LegacyErrorException.class)
    ResponseEntity<ProblemDetail> handleLegacyError(LegacyErrorException e) {
        // No detail: it would only repeat the title, which is already the human-readable summary.
        ProblemDetail problem = problem(HttpStatus.valueOf(e.status()), e.title(), null, e.code());

        if (e.legacyMessage() != null && !e.legacyMessage().isBlank()) {
            problem.setProperty("legacyMessage", e.legacyMessage());
        }
        if (e.legacyType() != null && !UNINFORMATIVE_LEGACY_TYPE.equals(e.legacyType())) {
            problem.setProperty("legacyType", e.legacyType());
        }
        if (e.payload() != null) {
            // A legacy failure can carry data worth keeping; see LegacyErrorException.
            problem.setProperty("payload", e.payload());
        }
        if (!e.isRecognised()) {
            log.warn(
                    "unrecognised legacy error, add it to error-catalog.yaml: type={} message={}",
                    e.legacyType(),
                    e.legacyMessage());
        }
        return ResponseEntity.status(e.status()).body(problem);
    }

    @ExceptionHandler(LegacyProtocolException.class)
    ProblemDetail handleProtocolFailure(LegacyProtocolException e) {
        if (e.kind() == LegacyProtocolException.Kind.CHALLENGE) {
            // Not the legacy application's fault: the request never reached it.
            log.error(
                    "the legacy API was not reached — an upstream bot-protection challenge answered "
                            + "instead. The bridge host most likely needs a WAF allowlist entry.");
        } else {
            log.error("the legacy API returned something unusable: {}", e.getMessage());
        }
        return problem(
                HttpStatus.BAD_GATEWAY,
                "Unusable response from the legacy service",
                e.getMessage(),
                e.kind().code());
    }

    @ExceptionHandler(LegacyUnreachableException.class)
    ProblemDetail handleUnreachable(LegacyUnreachableException e) {
        boolean timedOut = isTimeout(e);
        log.error("legacy API unreachable: {}", e.getMessage());

        return problem(
                timedOut ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
                timedOut ? "The legacy service timed out" : "The legacy service is unreachable",
                e.getMessage(),
                timedOut ? "UPSTREAM_TIMEOUT" : "UPSTREAM_UNREACHABLE");
    }

    @ExceptionHandler(TranslationException.class)
    ProblemDetail handleTranslationFailure(TranslationException e) {
        log.error("translation failed, the mapping and the payload disagree: {}", e.getMessage());
        return problem(
                HttpStatus.BAD_GATEWAY,
                "The legacy payload did not fit its mapping",
                e.getMessage(),
                "MAPPING_MISMATCH");
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
    ProblemDetail handleUnexpected(Exception e) {
        log.error("unhandled exception while serving a request", e);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal error",
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

    /**
     * @param detail may be {@code null}, in which case the field is omitted rather than repeating
     *     the title
     */
    private static ProblemDetail problem(
            HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        if (detail != null && !detail.equals(title)) {
            problem.setDetail(detail);
        }
        problem.setProperty("code", code);
        return problem;
    }
}
