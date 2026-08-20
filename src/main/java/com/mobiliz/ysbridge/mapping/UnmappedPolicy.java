package com.mobiliz.ysbridge.mapping;

/** What to do with a field that has no mapping entry. */
public enum UnmappedPolicy {

    /** Copy the field through unchanged and record it so the gap becomes visible. */
    PASSTHROUGH_AND_REPORT,

    /** Silently omit the field from the translated payload. */
    DROP,

    /**
     * Reject the payload. Intended for inbound requests, where unknown fields are client errors.
     */
    FAIL
}
