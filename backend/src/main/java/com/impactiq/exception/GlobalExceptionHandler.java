package com.impactiq.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DefectNotFoundException.class)
    public ResponseEntity<ApiError> handleDefectNotFound(DefectNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ImpactAnalysisNotFoundException.class)
    public ResponseEntity<ApiError> handleImpactAnalysisNotFound(ImpactAnalysisNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException exception) {
        return error(exception.status(), exception.getMessage(), Map.of());
    }

    @ExceptionHandler(RetryableApiException.class)
    public ResponseEntity<ApiError> handleRetryable(RetryableApiException exception) {
        ResponseEntity<ApiError> response = error(exception.status(), exception.getMessage(), Map.of());
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()))
                .body(response.getBody());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge() {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the allowed size", Map.of());
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ApiError> handleBadMultipart() {
        return error(HttpStatus.BAD_REQUEST, "A valid file upload is required", Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody() {
        return error(HttpStatus.BAD_REQUEST, "Request body is malformed or contains invalid values", Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied() {
        return error(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", Map.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDatabaseFailure() {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to complete the database operation", Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                validationErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Request validation failed", validationErrors);
    }

    @ExceptionHandler(ImpactAnalysisException.InvalidModelResponse.class)
    public ResponseEntity<ApiError> handleInvalidModelResponse() {
        return error(HttpStatus.BAD_GATEWAY, "Gemini returned an invalid impact analysis response", Map.of());
    }

    @ExceptionHandler(GeminiServiceException.class)
    public ResponseEntity<ApiError> handleGeminiFailure(GeminiServiceException exception) {
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        String message = "Gemini impact analysis is temporarily unavailable";
        if (exception instanceof GeminiServiceException.MissingApiKey) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Gemini API key is not configured";
        } else if (exception instanceof GeminiServiceException.ApiFailure apiFailure
                && (apiFailure.statusCode() == 429 || apiFailure.statusCode() == 503)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = apiFailure.statusCode() == 429
                    ? "Gemini rate limit reached. Retry shortly."
                    : "Gemini is temporarily overloaded. Retry shortly.";
        } else if (exception instanceof GeminiServiceException.RequestTimeout) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            message = "Gemini request timed out";
        } else if (exception instanceof GeminiServiceException.AuthenticationFailure) {
            message = "Gemini rejected the configured credentials";
        }
        return error(status, message, Map.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, Map<String, String> validationErrors) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                validationErrors
        );
        return ResponseEntity.status(status).body(body);
    }
}
