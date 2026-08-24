package com.mobiliz.ysbridge.gateway.exception;

/**
 * A query parameter the legacy API requires as a header holds a value that cannot travel in one.
 *
 * <p>The legacy API reads a search term and a camera channel from headers, so those values leave
 * this service as header values. A header value may not contain control characters: a carriage
 * return or line feed would end the header and let whatever follows be read as another one.
 *
 * <p>The HTTP client rejects such a value anyway, so nothing is smuggled upstream either way. This
 * exists so the rejection is a {@code 400} naming the offending parameter rather than a {@code 500}
 * with a stack trace — a caller's mistake should not read as this service failing, and should not
 * let anyone fill the error log by sending one request.
 */
public class InvalidQueryParameterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient String parameter;

    public InvalidQueryParameterException(String parameter) {
        super(
                "query parameter '%s' contains a character that cannot be sent in a header"
                        .formatted(parameter));
        this.parameter = parameter;
    }

    public String parameter() {
        return parameter;
    }
}
