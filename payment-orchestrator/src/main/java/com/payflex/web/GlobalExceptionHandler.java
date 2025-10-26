package com.payflex.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(WebExchangeBindException.class)
  public ResponseEntity<Map<String, Object>> handleValidationException(WebExchangeBindException ex) {
    log.error("Validation error: {}", ex.getMessage());

    Map<String, Object> errors = new HashMap<>();
    errors.put("error", "Validation Failed");
    errors.put("status", 400);
    errors.put("details", ex.getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .collect(Collectors.toList()));

    return ResponseEntity.badRequest().body(errors);
  }

  @ExceptionHandler(ServerWebInputException.class)
  public ResponseEntity<Map<String, Object>> handleInputException(ServerWebInputException ex) {
    log.error("Input error: {}", ex.getMessage(), ex);

    Map<String, Object> errors = new HashMap<>();
    errors.put("error", "Invalid Request");
    errors.put("status", 400);
    errors.put("message", ex.getReason());

    return ResponseEntity.badRequest().body(errors);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
    log.error("Access denied: {}", ex.getMessage());

    Map<String, Object> errors = new HashMap<>();
    errors.put("error", "Forbidden");
    errors.put("status", 403);
    errors.put("message", ex.getMessage());

    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errors);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
    log.error("Unexpected error: {}", ex.getMessage(), ex);

    Map<String, Object> errors = new HashMap<>();
    errors.put("error", "Internal Server Error");
    errors.put("status", 500);
    errors.put("message", ex.getMessage());

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errors);
  }
}

