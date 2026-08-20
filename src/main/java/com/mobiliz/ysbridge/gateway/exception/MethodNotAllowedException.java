package com.mobiliz.ysbridge.gateway.exception;

import java.util.List;
import org.springframework.http.HttpMethod;

/** The path exists, but not under this method. */
public class MethodNotAllowedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<HttpMethod> allowed;

    public MethodNotAllowedException(String path, HttpMethod attempted, List<HttpMethod> allowed) {
        super("%s does not accept %s".formatted(path, attempted));
        this.allowed = List.copyOf(allowed);
    }

    public List<HttpMethod> allowed() {
        return allowed;
    }
}
