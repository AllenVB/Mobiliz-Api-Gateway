package com.mobiliz.ysbridge.gateway.exception;

/** A query parameter the legacy API requires as a header was not supplied. */
public class MissingQueryParameterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient String parameter;

    public MissingQueryParameterException(String parameter) {
        super("required query parameter '%s' is missing".formatted(parameter));
        this.parameter = parameter;
    }

    public String parameter() {
        return parameter;
    }
}
