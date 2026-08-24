package com.mobiliz.ysbridge.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds the envelope every exposed response is wrapped in.
 *
 * <p>The legacy API wraps everything in {@code {success, type, message, result}}, and the consuming
 * system expects to keep receiving that shape. So the envelope stays — but everything wrong inside
 * it is corrected:
 *
 * <ul>
 *   <li><strong>The HTTP status is truthful.</strong> The legacy API answers {@code 201 Created} to
 *       reads and to errors alike; this service answers {@code 200}, {@code 401}, {@code 409} and
 *       so on. The envelope's {@code success} and the status agree rather than contradict.
 *   <li><strong>{@code type} is gone.</strong> It only ever held {@code "SUCCESS"} or {@code
 *       "ERROR"} — the same fact {@code success} already carries as a boolean, which is cheaper to
 *       branch on than a string comparison. If it ever carries something else it is logged, and it
 *       can be reinstated as a new field without breaking a caller; the reverse would not be true.
 *   <li><strong>A failure carries a stable {@code code}.</strong> The legacy {@code message} is
 *       Turkish free text that no client can branch on. The code is what a client reads; the
 *       message is for a human, and the untranslated original travels alongside as {@code
 *       legacyMessage} so support can still trace it.
 * </ul>
 *
 * <p>Because the envelope is always present, {@code 204 No Content} is never used: a 204 may carry
 * no body, and dropping the envelope for the one case where {@code result} is null would make the
 * contract conditional. An empty success is {@code 200} with {@code "result": null}.
 */
public final class ExposedEnvelope {

    public static final String SUCCESS = "success";
    public static final String MESSAGE = "message";
    public static final String CODE = "code";
    public static final String RESULT = "result";
    public static final String LEGACY_MESSAGE = "legacyMessage";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private ExposedEnvelope() {}

    /**
     * @param result the translated payload, or a {@code NullNode} when the operation returns none
     */
    public static ObjectNode success(JsonNode result) {
        ObjectNode envelope = NODES.objectNode();
        envelope.put(SUCCESS, true);
        envelope.putNull(MESSAGE);
        envelope.set(RESULT, result == null ? NODES.nullNode() : result);
        return envelope;
    }

    /**
     * A failure envelope. Callers may append further diagnostic fields to the returned node.
     *
     * @param result normally null, but a legacy failure can carry data worth keeping — {@code
     *     cihaztamamlandi} answers {@code success: false} alongside a populated result, and
     *     dropping it would lose information the caller asked for
     */
    public static ObjectNode failure(String message, String code, JsonNode result) {
        ObjectNode envelope = NODES.objectNode();
        envelope.put(SUCCESS, false);
        envelope.put(MESSAGE, message);
        envelope.put(CODE, code);
        envelope.set(RESULT, result == null ? NODES.nullNode() : result);
        return envelope;
    }

    public static ObjectNode failure(String message, String code) {
        return failure(message, code, null);
    }
}
