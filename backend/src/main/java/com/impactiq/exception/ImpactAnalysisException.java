package com.impactiq.exception;

public class ImpactAnalysisException extends RuntimeException {

    public ImpactAnalysisException(String message) {
        super(message);
    }

    public static final class InvalidModelResponse extends ImpactAnalysisException {
        public InvalidModelResponse() {
            super("Gemini returned an invalid impact analysis response");
        }
    }
}
