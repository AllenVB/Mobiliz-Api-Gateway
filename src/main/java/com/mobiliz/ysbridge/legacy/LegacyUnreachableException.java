package com.mobiliz.ysbridge.legacy;

/** Raised when the legacy API could not be reached at all: no answer, not even a bad one. */
public class LegacyUnreachableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LegacyUnreachableException(String message, Throwable cause) {
        super(message, cause);
    }
}
