package com.mobiliz.ysbridge.mapping.model;

/** Whether an endpoint needs the caller to be authenticated. */
public enum AuthenticationRequirement {

    /**
     * A token is required. The gateway rejects the request with 401 before calling the legacy API,
     * rather than letting it answer "Token Bilgisi Eksik" a round trip later.
     */
    TOKEN,

    /** No token needed — these are the endpoints you use to obtain one. */
    NONE
}
