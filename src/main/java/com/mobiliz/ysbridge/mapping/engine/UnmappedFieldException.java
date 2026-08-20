package com.mobiliz.ysbridge.mapping.engine;

/**
 * Raised when a payload carries a field that has no mapping and the mapping declares {@code
 * unmapped: FAIL}. Intended for inbound requests, where an unrecognised field usually means the
 * caller is working from an outdated contract.
 */
public class UnmappedFieldException extends TranslationException {

    private static final long serialVersionUID = 1L;

    private final String path;

    public UnmappedFieldException(String path) {
        super("unmapped field '%s' is not allowed here".formatted(path));
        this.path = path;
    }

    public String path() {
        return path;
    }
}
