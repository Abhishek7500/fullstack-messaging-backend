package com.messenger.exception;

/**
 * Thrown when an action requires a higher subscription tier than the caller
 * currently holds (e.g. locking a capsule beyond the free window, or sharing
 * media inside a FREE group chat). Mapped to HTTP 403 by
 * {@code com.messenger.web.ApiExceptionHandler}.
 */
public class PremiumTierRequiredException extends RuntimeException {

    private final String feature;

    public PremiumTierRequiredException(String feature, String message) {
        super(message);
        this.feature = feature;
    }

    public PremiumTierRequiredException(String message) {
        this(null, message);
    }

    /** Short machine-readable identifier the client can branch on, if provided. */
    public String getFeature() {
        return feature;
    }
}
