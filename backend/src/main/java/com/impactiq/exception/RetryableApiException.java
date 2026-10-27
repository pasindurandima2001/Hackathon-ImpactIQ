package com.impactiq.exception;

import org.springframework.http.HttpStatus;

/** An {@link ApiException} the client may retry; the handler adds a Retry-After header (seconds). */
public class RetryableApiException extends ApiException {

    private final long retryAfterSeconds;

    public RetryableApiException(HttpStatus status, String message, long retryAfterSeconds) {
        super(status, message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public static RetryableApiException tooManyRequests(String message, long retryAfterSeconds) {
        return new RetryableApiException(HttpStatus.TOO_MANY_REQUESTS, message, retryAfterSeconds);
    }

    public static RetryableApiException unavailable(String message, long retryAfterSeconds) {
        return new RetryableApiException(HttpStatus.SERVICE_UNAVAILABLE, message, retryAfterSeconds);
    }
}
