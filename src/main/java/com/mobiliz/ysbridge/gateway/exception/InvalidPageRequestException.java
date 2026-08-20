package com.mobiliz.ysbridge.gateway.exception;

/** A paging parameter was present but unusable. */
public class InvalidPageRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient String parameter;

    public InvalidPageRequestException(String parameter, String problem) {
        super("query parameter '%s' %s".formatted(parameter, problem));
        this.parameter = parameter;
    }

    public String parameter() {
        return parameter;
    }
}
