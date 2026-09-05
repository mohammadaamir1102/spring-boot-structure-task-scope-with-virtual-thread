package com.aamir.exception;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        int statusCode,
        String error,
        String message,
        String timestamp,
        Map<String, Object> details
) {
    public ErrorResponse(int statusCode, String error, String message) {
        this(statusCode, error, message, LocalDateTime.now().toString(), Map.of());
    }

    public ErrorResponse(int statusCode, String error, String message, Map<String, Object> details) {
        this(statusCode, error, message, LocalDateTime.now().toString(), details);
    }
}
