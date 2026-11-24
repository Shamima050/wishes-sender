package com.wishessender.util;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Safety net: if the database rejects a row (UNIQUE key, CHECK or FOREIGN KEY constraint)
 * the user gets a 409/400 with a readable message instead of a 500 "Internal Server Error".
 * The JSON has the same shape as Spring's normal errors, so app.js shows data.message.
 */
@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, Object>> duplicate(DuplicateKeyException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "This record already exists", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> invalid(DataIntegrityViolationException e, HttpServletRequest request) {
        Throwable root = e.getMostSpecificCause();
        return error(HttpStatus.BAD_REQUEST, "Invalid data: " + root.getMessage(), request);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
