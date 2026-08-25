package com.mobiliz.ysbridge.gateway.exception;

/**
 * The request body is valid JSON, but not the shape the endpoint's contract describes.
 *
 * <p>A mapping declares fields, which means an object. A body that is an array, a string or a
 * number carries no fields at all, so {@code unmapped: FAIL} — which polices <em>which</em> fields
 * a body may carry — has nothing to police and lets it through. Without this check such a body is
 * relayed upstream verbatim and the caller is answered {@code 200}: a caller's mistake becoming
 * upstream load, and reading as success.
 *
 * <p>Distinct from {@link MalformedRequestBodyException} on purpose. "Your JSON is broken" and
 * "your JSON is fine but the wrong shape" are different problems with different fixes, and a client
 * should be able to tell them apart from the code alone.
 *
 * <p>Only endpoints that declare a request mapping are checked. {@code send-status-command}
 * declares none — its body has never been established (A16) — so there is no shape to violate and
 * whatever arrives is relayed.
 */
public class InvalidRequestShapeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient String actualType;

    public InvalidRequestShapeException(String actualType) {
        super(
                "this endpoint expects a JSON object, but the body was %s"
                        .formatted(actualType.toLowerCase()));
        this.actualType = actualType;
    }

    public String actualType() {
        return actualType;
    }
}
