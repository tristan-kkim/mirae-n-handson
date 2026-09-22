package com.example.common;

/** 조회 대상이 없을 때 서비스 계층이 던지는 예외. {@link GlobalExceptionHandler}가 404로 변환한다. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
