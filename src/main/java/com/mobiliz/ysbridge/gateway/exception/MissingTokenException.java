package com.mobiliz.ysbridge.gateway.exception;

/**
 * The endpoint requires authentication and the caller sent none.
 *
 * <p>Rejected here rather than forwarded: letting the legacy API answer "Token Bilgisi Eksik" costs
 * a round trip and turns a caller mistake into upstream load.
 */
public class MissingTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MissingTokenException() {
        super("this endpoint requires an Authorization: Bearer <token> header");
    }
}
