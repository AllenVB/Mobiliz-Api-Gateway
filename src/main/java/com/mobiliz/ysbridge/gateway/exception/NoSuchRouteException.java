package com.mobiliz.ysbridge.gateway.exception;

import org.springframework.http.HttpMethod;

/** No exposed route serves this method and path. */
public class NoSuchRouteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NoSuchRouteException(HttpMethod method, String path) {
        super("no endpoint serves %s %s".formatted(method, path));
    }
}
