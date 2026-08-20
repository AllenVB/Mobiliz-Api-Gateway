package com.mobiliz.ysbridge.mapping.engine;

/** Raised when a payload cannot be translated against its mapping. */
public class TranslationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TranslationException(String message) {
        super(message);
    }
}
