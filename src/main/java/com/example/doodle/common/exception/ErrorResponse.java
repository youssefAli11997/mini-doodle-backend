package com.example.doodle.common.exception;

public record ErrorResponse(
        String code,
        String message
) {
}
