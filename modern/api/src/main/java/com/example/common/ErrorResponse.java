package com.example.common;

import java.time.Instant;

/** 모든 오류 응답의 공통 모양. */
public record ErrorResponse(int status, String error, String message, String path, Instant timestamp) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(status, error, message, path, Instant.now());
    }
}
