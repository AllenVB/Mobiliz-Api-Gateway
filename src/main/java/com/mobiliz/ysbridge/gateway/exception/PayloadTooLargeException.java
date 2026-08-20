package com.mobiliz.ysbridge.gateway.exception;

/**
 * The request body exceeds the configured bound.
 *
 * <p>Worth enforcing here: attachments travel as base64 inside JSON, so an unbounded gateway would
 * hold whole files in memory on behalf of any caller who asked.
 */
public class PayloadTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient long limitBytes;

    public PayloadTooLargeException(long limitBytes) {
        super("the request body exceeds the %d byte limit".formatted(limitBytes));
        this.limitBytes = limitBytes;
    }

    public long limitBytes() {
        return limitBytes;
    }
}
