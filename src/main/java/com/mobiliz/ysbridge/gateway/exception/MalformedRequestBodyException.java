package com.mobiliz.ysbridge.gateway.exception;

/** The request body was not JSON. */
public class MalformedRequestBodyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MalformedRequestBodyException(Throwable cause) {
        super("the request body is not valid JSON", cause);
    }
}
