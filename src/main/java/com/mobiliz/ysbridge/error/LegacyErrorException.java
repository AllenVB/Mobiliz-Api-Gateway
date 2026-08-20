package com.mobiliz.ysbridge.error;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A business failure reported by the legacy API, already translated into the exposed error
 * vocabulary.
 *
 * <p>The original {@code type} and {@code message} travel along so support can trace an exposed
 * error back to the legacy log line that produced it.
 */
public class LegacyErrorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient int status;
    private final transient String code;
    private final transient String title;
    private final transient String legacyType;
    private final transient String legacyMessage;
    private final transient JsonNode payload;
    private final transient boolean recognised;

    public LegacyErrorException(
            int status,
            String code,
            String title,
            String legacyType,
            String legacyMessage,
            JsonNode payload,
            boolean recognised) {
        super(title);
        this.status = status;
        this.code = code;
        this.title = title;
        this.legacyType = legacyType;
        this.legacyMessage = legacyMessage;
        this.payload = payload;
        this.recognised = recognised;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public String legacyType() {
        return legacyType;
    }

    public String legacyMessage() {
        return legacyMessage;
    }

    /**
     * The {@code result} the legacy API sent alongside the failure, or {@code null}.
     *
     * <p>A failure can carry data: {@code cihaztamamlandi} answers {@code success: false} together
     * with {@code {"durumu": false}}. Dropping it would lose the only useful part of the response.
     */
    public JsonNode payload() {
        return payload;
    }

    /** Whether the legacy message was in the catalog, or fell through to the default. */
    public boolean isRecognised() {
        return recognised;
    }
}
