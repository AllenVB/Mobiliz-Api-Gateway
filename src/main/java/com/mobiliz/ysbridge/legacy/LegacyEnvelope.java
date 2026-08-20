package com.mobiliz.ysbridge.legacy;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The wrapper every legacy response arrives in.
 *
 * <p>The legacy API answers {@code 201 Created} for reads and for failures alike, so its HTTP
 * status carries no information. {@link #success()} is the only reliable signal.
 *
 * @param success whether the call succeeded, according to the body
 * @param type legacy classification, e.g. {@code SUCCESS} or {@code ERROR}
 * @param message legacy message, free-form Turkish text; {@code null} on success
 * @param result payload; may be an object, an array, or a null node
 */
public record LegacyEnvelope(boolean success, String type, String message, JsonNode result) {

    /** Whether the call succeeded and carried no payload, which maps to 204 No Content. */
    public boolean isEmptySuccess() {
        return success && (result == null || result.isNull() || result.isMissingNode());
    }
}
