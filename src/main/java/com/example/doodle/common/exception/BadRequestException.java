package com.example.doodle.common.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends AppException {

    public BadRequestException(String code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }

    public BadRequestException(String message) {
        this("BAD_REQUEST", message);
    }
}
