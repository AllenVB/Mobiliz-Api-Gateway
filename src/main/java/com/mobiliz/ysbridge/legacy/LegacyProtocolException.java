package com.mobiliz.ysbridge.legacy;

/**
 * Raised when the legacy API answers with something that is not its envelope at all.
 *
 * <p>The {@link Kind} distinguishes cases that look alike from the outside but need different
 * responses from whoever is on call. A bot-protection challenge means the request never reached the
 * application and the WAF needs a rule; malformed JSON means the application answered and something
 * is wrong with it. Collapsing both into "bad gateway" would send people to the wrong place.
 *
 * <p>The message is written to be safe to show a caller: it says what shape came back, never the
 * body itself, which may hold details that should not leave the server.
 */
public class LegacyProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** What was wrong with the response, and therefore who should look at it. */
    public enum Kind {
        /** An upstream bot-protection interstitial. The request never reached the API. */
        CHALLENGE("UPSTREAM_CHALLENGE"),
        /** A redirect, which this gateway deliberately does not follow. */
        REDIRECT("UPSTREAM_REDIRECT"),
        /** HTML, or another non-JSON body. */
        NOT_JSON("UPSTREAM_NOT_JSON"),
        /** No body at all. */
        EMPTY_BODY("UPSTREAM_EMPTY_BODY"),
        /** A body that would not parse as JSON. */
        MALFORMED_JSON("UPSTREAM_MALFORMED_JSON"),
        /**
         * Well-formed JSON that exceeded the parser's bounds — a single string longer than Jackson
         * allows, most likely. Distinct from {@link #MALFORMED_JSON} because the two send whoever
         * is on call to different places: one is a bug in the legacy application's output, the
         * other is a volume the parser was never configured for.
         */
        RESPONSE_TOO_LARGE("UPSTREAM_RESPONSE_TOO_LARGE"),
        /** Valid JSON, but not the legacy envelope — ASP.NET's own error body, for instance. */
        NOT_AN_ENVELOPE("UPSTREAM_NOT_AN_ENVELOPE");

        private final String code;

        Kind(String code) {
            this.code = code;
        }

        /** Stable code the caller can branch on. */
        public String code() {
            return code;
        }
    }

    private final transient Kind kind;

    public LegacyProtocolException(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public LegacyProtocolException(Kind kind, String reason, Throwable cause) {
        super(reason, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /**
     * Whether the response was a bot-protection challenge rather than a fault in the legacy
     * application. Worth distinguishing: it points at the WAF configuration for the bridge host,
     * not at the API.
     */
    public boolean isUpstreamChallenge() {
        return kind == Kind.CHALLENGE;
    }
}
