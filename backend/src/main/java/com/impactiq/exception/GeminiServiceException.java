package com.impactiq.exception;

public class GeminiServiceException extends RuntimeException {

    protected GeminiServiceException(String message) {
        super(message);
    }

    public static final class MissingApiKey extends GeminiServiceException {
        public MissingApiKey() {
            super("GEMINI_API_KEY is not configured");
        }
    }

    public static final class AuthenticationFailure extends GeminiServiceException {
        public AuthenticationFailure() {
            super("Gemini rejected the configured credentials");
        }
    }

    public static final class ApiFailure extends GeminiServiceException {
        private final int statusCode;

        public ApiFailure(int statusCode) {
            super("Gemini API request failed with status " + statusCode);
            this.statusCode = statusCode;
        }

        public ApiFailure() {
            super("Gemini API request failed");
            this.statusCode = 0;
        }

        public int statusCode() {
            return statusCode;
        }
    }

    public static final class RequestTimeout extends GeminiServiceException {
        public RequestTimeout() {
            super("Gemini API request timed out");
        }
    }

    public static final class MalformedResponse extends GeminiServiceException {
        public MalformedResponse() {
            super("Gemini returned an empty or malformed text response");
        }
    }
}
