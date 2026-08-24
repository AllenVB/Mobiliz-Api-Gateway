package com.mobiliz.ysbridge.gateway;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobiliz.ysbridge.config.BridgeProperties;
import com.mobiliz.ysbridge.error.ErrorCatalog;
import com.mobiliz.ysbridge.error.ErrorRule;
import com.mobiliz.ysbridge.error.LegacyErrorException;
import com.mobiliz.ysbridge.gateway.exception.InvalidQueryParameterException;
import com.mobiliz.ysbridge.gateway.exception.MalformedRequestBodyException;
import com.mobiliz.ysbridge.gateway.exception.MethodNotAllowedException;
import com.mobiliz.ysbridge.gateway.exception.MissingQueryParameterException;
import com.mobiliz.ysbridge.gateway.exception.MissingTokenException;
import com.mobiliz.ysbridge.gateway.exception.NoSuchRouteException;
import com.mobiliz.ysbridge.gateway.exception.PayloadTooLargeException;
import com.mobiliz.ysbridge.gateway.exception.UnsupportedMediaTypeException;
import com.mobiliz.ysbridge.legacy.EnvelopeParser;
import com.mobiliz.ysbridge.legacy.LegacyApiClient;
import com.mobiliz.ysbridge.legacy.LegacyEnvelope;
import com.mobiliz.ysbridge.legacy.LegacyProtocolException;
import com.mobiliz.ysbridge.legacy.LegacyResponse;
import com.mobiliz.ysbridge.mapping.Direction;
import com.mobiliz.ysbridge.mapping.engine.JsonTranslator;
import com.mobiliz.ysbridge.mapping.model.LegacyHeaderMapping;
import com.mobiliz.ysbridge.mapping.model.ResourceMapping;
import com.mobiliz.ysbridge.observability.MappingCoverageRecorder;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The single entry point of the bridge.
 *
 * <p>One handler serves every exposed endpoint. There are no per-endpoint controllers, because
 * there is no per-endpoint logic: what changes between endpoints lives entirely in the mapping
 * documents. Adding an endpoint is a configuration change.
 *
 * <p>The request path is:
 *
 * <ol>
 *   <li>match the route, or answer 404 / 405
 *   <li>require a token unless the mapping says the endpoint is public
 *   <li>read the body within a bound, and reject anything that is not JSON
 *   <li>move mapped query parameters into the headers the legacy API expects
 *   <li>translate the request body into the legacy vocabulary
 *   <li>call the legacy API
 *   <li>interpret its envelope — the body decides success, never the status
 *   <li>translate the payload back, page it if it is a collection, and answer
 * </ol>
 */
@RestController
public class BridgeController {

    /** Header the legacy API reads the token from. Not what this API exposes. */
    private static final String LEGACY_TOKEN_HEADER = "token";

    private static final String BEARER_PREFIX = "Bearer ";

    private final RouteResolver routeResolver;
    private final JsonTranslator translator;
    private final LegacyApiClient legacyApiClient;
    private final EnvelopeParser envelopeParser;
    private final ErrorCatalog errorCatalog;
    private final MappingCoverageRecorder coverage;
    private final CollectionPaginator paginator;
    private final ObjectMapper objectMapper;
    private final int maxRequestBodyBytes;

    public BridgeController(
            RouteResolver routeResolver,
            JsonTranslator translator,
            LegacyApiClient legacyApiClient,
            EnvelopeParser envelopeParser,
            ErrorCatalog errorCatalog,
            MappingCoverageRecorder coverage,
            CollectionPaginator paginator,
            ObjectMapper objectMapper,
            BridgeProperties properties) {
        this.routeResolver = routeResolver;
        this.translator = translator;
        this.legacyApiClient = legacyApiClient;
        this.envelopeParser = envelopeParser;
        this.errorCatalog = errorCatalog;
        this.coverage = coverage;
        this.paginator = paginator;
        this.objectMapper = objectMapper;
        this.maxRequestBodyBytes =
                (int)
                        Math.min(
                                properties.limits().maxRequestBody().toBytes(),
                                Integer.MAX_VALUE - 1);
    }

    @RequestMapping("/authorized-service/**")
    public ResponseEntity<JsonNode> handle(HttpServletRequest request)
            throws HttpMediaTypeNotAcceptableException {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        String path = pathOf(request);

        RouteResolver.RouteMatch match =
                routeResolver.resolve(method, path).orElseThrow(() -> noRoute(method, path));
        ResourceMapping resource = match.resource();

        String token = bearerToken(request);
        if (resource.requiresToken() && token == null) {
            throw new MissingTokenException();
        }

        // Validated before the call, not after: a request the caller got wrong must never
        // reach the legacy API. Paging parameters are checked even on endpoints that do not
        // return a collection — a parameter this API understands but cannot use is still a
        // mistake worth naming.
        PageRequest pageRequest = paginator.parse(request);

        requireJsonIsAcceptable(request);

        byte[] rawBody = readBody(request);
        requireJsonWhenBodyPresent(request, rawBody);

        HttpHeaders headers = buildLegacyHeaders(request, resource, token);
        String legacyBody = buildLegacyBody(rawBody, resource, headers);

        LegacyResponse legacyResponse =
                legacyApiClient.exchange(
                        method, resource.legacyPath(), match.pathVariables(), headers, legacyBody);
        rejectRedirect(legacyResponse);

        LegacyEnvelope envelope =
                envelopeParser.parse(legacyResponse.body(), legacyResponse.contentType());

        if (!envelope.success()) {
            throw toExposedError(envelope, resource);
        }
        // 200 with a null result rather than 204: the envelope is part of the contract, and a
        // 204 may carry no body. See ExposedEnvelope.
        if (envelope.isEmptySuccess()) {
            return ResponseEntity.ok(ExposedEnvelope.success(null));
        }

        JsonNode result = envelope.result();
        if (result.isArray()) {
            return ResponseEntity.ok(ExposedEnvelope.success(page(result, resource, pageRequest)));
        }
        return ResponseEntity.ok(ExposedEnvelope.success(translateResult(result, resource)));
    }

    // ------------------------------------------------------------------ routing

    private static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath == null || contextPath.isEmpty()
                ? uri
                : uri.substring(contextPath.length());
    }

    private RuntimeException noRoute(HttpMethod method, String path) {
        if (routeResolver.pathExistsUnderAnotherMethod(path)) {
            return new MethodNotAllowedException(path, method, routeResolver.methodsFor(path));
        }
        return new NoSuchRouteException(method, path);
    }

    // --------------------------------------------------------------- inbound

    /**
     * Reads the token from a standard {@code Authorization: Bearer} header.
     *
     * <p>The legacy API takes it in a bare {@code token} header. Passing that awkwardness on to
     * callers would defeat the point of this layer, so the exposed contract uses the standard
     * scheme and the translation happens here — the same move as turning its {@code search} header
     * into a query parameter.
     */
    private static String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null
                || !authorization.regionMatches(
                        true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * Reads the body without ever holding more than the configured bound.
     *
     * <p>Read here rather than through {@code @RequestBody} so the limit is enforced while reading.
     * Attachments arrive as base64 inside JSON, so an unbounded gateway would buffer whole files on
     * behalf of any caller who asked.
     */
    private byte[] readBody(HttpServletRequest request) {
        if (request.getContentLengthLong() > maxRequestBodyBytes) {
            throw new PayloadTooLargeException(maxRequestBodyBytes);
        }
        try (InputStream stream = request.getInputStream()) {
            byte[] body = stream.readNBytes(maxRequestBodyBytes + 1);
            if (body.length > maxRequestBodyBytes) {
                throw new PayloadTooLargeException(maxRequestBodyBytes);
            }
            return body;
        } catch (IOException e) {
            throw new MalformedRequestBodyException(e);
        }
    }

    /**
     * Rejects a caller who cannot accept JSON, before the legacy API is called.
     *
     * <p>Left to the framework, this surfaces only while writing the response — after the upstream
     * call has already been made and paid for. The header says up front that the exchange cannot
     * succeed, so it is checked up front.
     */
    private static void requireJsonIsAcceptable(HttpServletRequest request)
            throws HttpMediaTypeNotAcceptableException {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null || accept.isBlank()) {
            return;
        }
        try {
            for (MediaType accepted : MediaType.parseMediaTypes(accept)) {
                if (accepted.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                    return;
                }
            }
        } catch (InvalidMediaTypeException e) {
            throw new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));
        }
        throw new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));
    }

    private static void requireJsonWhenBodyPresent(HttpServletRequest request, byte[] rawBody) {
        if (rawBody.length == 0) {
            return;
        }
        String declared = request.getContentType();
        if (declared == null || declared.isBlank()) {
            throw new UnsupportedMediaTypeException("(none)");
        }
        try {
            if (!MediaType.parseMediaType(declared).isCompatibleWith(MediaType.APPLICATION_JSON)) {
                throw new UnsupportedMediaTypeException(declared);
            }
        } catch (InvalidMediaTypeException e) {
            throw new UnsupportedMediaTypeException(declared);
        }
    }

    /**
     * Builds the headers that leave this service: the token, the caller's language preference, and
     * any header the legacy API reads which the exposed contract takes as a query parameter.
     *
     * <p>An allow-list, not a filter: nothing the caller sends travels onward unless it is named
     * here.
     */
    private HttpHeaders buildLegacyHeaders(
            HttpServletRequest request, ResourceMapping resource, String token) {
        HttpHeaders headers = new HttpHeaders();

        if (token != null) {
            headers.set(LEGACY_TOKEN_HEADER, token);
        }
        String acceptLanguage = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }

        for (LegacyHeaderMapping header : resource.legacyHeaders()) {
            String value = request.getParameter(header.fromQuery());
            if (value == null || value.isBlank()) {
                if (header.required()) {
                    throw new MissingQueryParameterException(header.fromQuery());
                }
                continue;
            }
            requireUsableAsHeaderValue(value, header.fromQuery());
            headers.set(header.legacyName(), value);
        }
        return headers;
    }

    /**
     * Rejects a value that cannot travel in a header, before the upstream call is attempted.
     *
     * <p>The HTTP client refuses such a value too, so nothing is smuggled upstream either way — but
     * it refuses by throwing, which would surface as a 500 and log a stack trace. A caller's
     * mistake should not read as this service failing, and one request should not be enough to
     * start filling the error log.
     */
    private static void requireUsableAsHeaderValue(String value, String parameter) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                throw new InvalidQueryParameterException(parameter);
            }
        }
    }

    /** Translates the inbound body into the legacy vocabulary, or relays it unchanged. */
    private String buildLegacyBody(byte[] rawBody, ResourceMapping resource, HttpHeaders headers) {
        if (rawBody.length == 0) {
            return null;
        }
        headers.setContentType(MediaType.APPLICATION_JSON);

        JsonNode exposedBody;
        try {
            exposedBody = objectMapper.readTree(new String(rawBody, StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            throw new MalformedRequestBodyException(e);
        }
        if (resource.request() == null) {
            // No captured shape for this endpoint; the body is relayed untranslated.
            return exposedBody.toString();
        }
        JsonNode legacyBody =
                translator.translate(
                        exposedBody,
                        resource.request(),
                        Direction.TO_LEGACY,
                        coverage.observerFor(resource.name()));
        return legacyBody.toString();
    }

    // ---------------------------------------------------------------- outbound

    /**
     * A redirect means the request was sent somewhere this gateway will not follow. Diagnosed
     * separately from a malformed body: the fix is a configuration change, not a code one.
     */
    private static void rejectRedirect(LegacyResponse response) {
        if (response.status() >= 300 && response.status() < 400) {
            throw new LegacyProtocolException(
                    LegacyProtocolException.Kind.REDIRECT,
                    "the legacy API answered with a redirect (%d), which this gateway does not follow"
                            .formatted(response.status()));
        }
    }

    /**
     * Slices the collection before translating it, so a page costs a page's worth of work rather
     * than the whole collection's. The reference-data endpoint answers with roughly a megabyte.
     */
    private JsonNode page(JsonNode array, ResourceMapping resource, PageRequest pageRequest) {
        int totalElements = array.size();
        JsonNode translated = translateResult(paginator.slice(array, pageRequest), resource);
        return paginator.wrap(translated, pageRequest, totalElements);
    }

    private JsonNode translateResult(JsonNode result, ResourceMapping resource) {
        if (resource.response() == null) {
            // Reported rather than passed over in silence: an endpoint with no response mapping
            // is the largest gap there is, and it is the one the coverage report used to miss.
            if (result.isObject()) {
                coverage.unmappedResponse(resource.name(), result::fieldNames);
            }
            return result;
        }
        return translator.translate(
                result,
                resource.response(),
                Direction.TO_ENGLISH,
                coverage.observerFor(resource.name()));
    }

    /**
     * Turns a legacy failure into an exposed error.
     *
     * <p>The payload that came with the failure is translated too: {@code cihaztamamlandi} answers
     * {@code success: false} alongside real data, and dropping it would lose the only useful part
     * of the response.
     */
    private LegacyErrorException toExposedError(LegacyEnvelope envelope, ResourceMapping resource) {
        Optional<ErrorRule> rule = errorCatalog.find(envelope.message());
        JsonNode payload =
                envelope.isEmptySuccess() || envelope.result() == null
                        ? null
                        : translateResult(envelope.result(), resource);

        return rule.map(
                        matched ->
                                new LegacyErrorException(
                                        matched.status(),
                                        matched.code(),
                                        matched.title(),
                                        envelope.type(),
                                        envelope.message(),
                                        payload,
                                        true))
                .orElseGet(
                        () ->
                                new LegacyErrorException(
                                        ErrorCatalog.UNRECOGNISED_STATUS,
                                        ErrorCatalog.UNRECOGNISED_CODE,
                                        ErrorCatalog.UNRECOGNISED_TITLE,
                                        envelope.type(),
                                        envelope.message(),
                                        payload,
                                        false));
    }
}
