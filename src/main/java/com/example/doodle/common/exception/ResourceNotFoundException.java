package com.example.doodle.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String code, String message) {
        super(code, message, HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String message) {
        this("RESOURCE_NOT_FOUND", message);
    }
}
