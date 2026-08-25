package com.mobiliz.ysbridge.legacy;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobiliz.ysbridge.legacy.LegacyProtocolException.Kind;
import java.util.List;
import java.util.Locale;
import org.springframework.http.MediaType;

/**
 * Turns a raw legacy response into a {@link LegacyEnvelope}.
 *
 * <p>Written defensively, because the legacy API is behind Cloudflare and does not always answer
 * with its own envelope: a bot-protection challenge arrives as an HTML page with a 403. Parsing
 * that blindly would surface as a stack trace instead of a diagnosis.
 */
public final class EnvelopeParser {

    /** Markers of a bot-protection interstitial, matched case-insensitively. */
    private static final List<String> CHALLENGE_MARKERS =
            List.of(
                    "challenges.cloudflare.com",
                    "cf_chl_opt",
                    "__cf_chl",
                    "cf-mitigated",
                    "just a moment...");

    /** Only the beginning of the body is scanned; a challenge page announces itself early. */
    private static final int MARKER_SCAN_LIMIT = 8192;

    private final ObjectMapper objectMapper;

    public EnvelopeParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param body raw response body, possibly {@code null}
     * @param contentType declared content type, possibly {@code null}
     * @throws LegacyProtocolException if the body is not the legacy envelope
     */
    public LegacyEnvelope parse(String body, MediaType contentType) {
        if (body == null || body.isBlank()) {
            throw new LegacyProtocolException(
                    Kind.EMPTY_BODY, "the legacy API returned an empty body");
        }
        if (looksLikeChallenge(body)) {
            throw new LegacyProtocolException(
                    Kind.CHALLENGE,
                    "the legacy API was not reached: an upstream bot-protection challenge "
                            + "answered instead of the API");
        }
        if (isHtml(body, contentType)) {
            throw new LegacyProtocolException(
                    Kind.NOT_JSON, "the legacy API returned HTML instead of JSON");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (StreamConstraintsException e) {
            // Well-formed, but past a bound the parser enforces. Reporting this as malformed
            // would send someone hunting for a syntax error that is not there. The
            // reference-data endpoint already answers with about a megabyte and will only
            // grow, so this is a failure mode worth naming for what it is.
            throw new LegacyProtocolException(
                    Kind.RESPONSE_TOO_LARGE,
                    "the legacy API returned a body larger than this gateway will parse",
                    e);
        } catch (JacksonException e) {
            throw new LegacyProtocolException(
                    Kind.MALFORMED_JSON,
                    "the legacy API returned a body that is not valid JSON",
                    e);
        }
        if (root == null || !root.isObject()) {
            throw new LegacyProtocolException(
                    Kind.NOT_AN_ENVELOPE,
                    "the legacy API returned JSON that is not an envelope object");
        }

        JsonNode success = root.get("success");
        if (success == null || !success.isBoolean()) {
            throw new LegacyProtocolException(
                    Kind.NOT_AN_ENVELOPE,
                    "the legacy response has no boolean 'success' field, so it cannot be "
                            + "interpreted");
        }

        return new LegacyEnvelope(
                success.booleanValue(),
                textOrNull(root.get("type")),
                textOrNull(root.get("message")),
                root.get("result"));
    }

    private static boolean looksLikeChallenge(String body) {
        String head =
                body.substring(0, Math.min(body.length(), MARKER_SCAN_LIMIT))
                        .toLowerCase(Locale.ROOT);
        for (String marker : CHALLENGE_MARKERS) {
            if (head.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHtml(String body, MediaType contentType) {
        if (contentType != null && contentType.isCompatibleWith(MediaType.TEXT_HTML)) {
            return true;
        }
        return body.stripLeading().startsWith("<");
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
