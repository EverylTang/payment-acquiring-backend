package com.example.payments.platform.service.controller;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class AdminExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException exception) {
    var message =
        exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " 参数无效")
            .orElse("请求参数无效");
    return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, String>> forbidden() {
    return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有执行该操作的权限");
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
    var code = exception.getStatusCode().value() == 401 ? "UNAUTHORIZED" : "REQUEST_REJECTED";
    return response(
        HttpStatus.valueOf(exception.getStatusCode().value()), code, exception.getReason());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
    return response(HttpStatus.BAD_REQUEST, "BUSINESS_ERROR", exception.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
    return response(HttpStatus.CONFLICT, "BUSINESS_CONFLICT", exception.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> unexpected(Exception exception) {
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂时不可用");
  }

  private ResponseEntity<Map<String, String>> response( HttpStatus status, String code, String message) {
    return ResponseEntity.status(status)
        .body(Map.of("code", code, "message", message == null ? "请求失败" : message));
  }
}
