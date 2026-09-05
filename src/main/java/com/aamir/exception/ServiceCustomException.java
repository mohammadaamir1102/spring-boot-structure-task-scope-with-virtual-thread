package com.aamir.exception;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ServiceCustomException extends RuntimeException {

    private final int statusCode;
    private final String timestamp;

    public ServiceCustomException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.timestamp = LocalDateTime.now().toString();
    }

    public ServiceCustomException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.timestamp = LocalDateTime.now().toString();
    }

}
