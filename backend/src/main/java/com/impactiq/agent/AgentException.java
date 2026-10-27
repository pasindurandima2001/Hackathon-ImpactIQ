package com.impactiq.agent;

/** Classified agent failure. The message is safe to persist and show; it never includes model output or secrets. */
public class AgentException extends RuntimeException {

    public enum Category {
        MISSING_API_KEY(false),
        AUTH_FAILED(false),
        RATE_LIMITED(true),
        MODEL_UNAVAILABLE(true),
        MODEL_TIMEOUT(true),
        INVALID_OUTPUT(true),
        RUN_TIMEOUT(false),
        ACCESS_DENIED(false),
        INTERNAL(false);

        private final boolean retryable;

        Category(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    private final Category category;
    private final int attempts;

    public AgentException(Category category, String message) {
        this(category, message, 1);
    }

    public AgentException(Category category, String message, int attempts) {
        super(message);
        this.category = category;
        this.attempts = attempts;
    }

    /** Model calls made before giving up. */
    public int attempts() {
        return attempts;
    }

    public Category category() {
        return category;
    }
}
