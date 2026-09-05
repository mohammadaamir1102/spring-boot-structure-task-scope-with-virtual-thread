package com.aamir.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.concurrent.StructuredTaskScope;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceCustomException.class)
    public ResponseEntity<ErrorResponse> handleServiceCustomException(ServiceCustomException ex) {
        var errorResponse = new ErrorResponse(
                ex.getStatusCode(),
                HttpStatus.valueOf(ex.getStatusCode()).getReasonPhrase(),
                ex.getMessage(),
                ex.getTimestamp(),
                Map.of("source", "service-layer")
        );
        return ResponseEntity.status(ex.getStatusCode()).body(errorResponse);
    }

    @ExceptionHandler(StructuredTaskScope.FailedException.class)
    public ResponseEntity<ErrorResponse> handleFailedException(StructuredTaskScope.FailedException ex) {
        var cause = ex.getCause();
        var message = cause != null ? cause.getMessage() : ex.getMessage();
        var errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "StructuredTaskScope Failure",
                message,
                Map.of(
                        "source", "structured-task-scope",
                        "causeType", cause != null ? cause.getClass().getSimpleName() : "unknown"
                )
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        var errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                ex.getMessage(),
                Map.of("source", "unknown", "exceptionType", ex.getClass().getSimpleName())
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
