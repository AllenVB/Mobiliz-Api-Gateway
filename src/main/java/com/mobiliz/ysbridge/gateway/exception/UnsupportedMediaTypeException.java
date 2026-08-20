package com.mobiliz.ysbridge.gateway.exception;

/** A body arrived declared as something other than JSON. */
public class UnsupportedMediaTypeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient String supplied;

    public UnsupportedMediaTypeException(String supplied) {
        super(
                "this API accepts application/json, but the request declared '%s'"
                        .formatted(supplied));
        this.supplied = supplied;
    }

    public String supplied() {
        return supplied;
    }
}
