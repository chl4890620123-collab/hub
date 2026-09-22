package com.hub.controller;

import com.hub.service.AuthException;
import com.hub.service.SheetLockedException;
import com.hub.service.StateConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> auth(AuthException exception) {
        return error(exception.code(), safe(exception));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(Exception exception) {
        return error("BAD_REQUEST", safe(exception));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .orElse("Request validation failed");
        return error("VALIDATION_FAILED", message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> denied(AccessDeniedException exception) {
        return error("FORBIDDEN", safe(exception));
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> notFound(EmptyResultDataAccessException ignored) {
        return error("NOT_FOUND", "요청한 데이터를 찾을 수 없습니다.");
    }

    @ExceptionHandler(SheetLockedException.class)
    @ResponseStatus(HttpStatus.LOCKED)
    public Map<String, Object> sheetLocked(SheetLockedException exception) {
        Map<String, Object> body = new java.util.HashMap<>(error("SHEET_LOCKED", safe(exception)));
        body.put("hint", exception.hint());
        return body;
    }

    @ExceptionHandler(StateConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> stateConflict(StateConflictException exception) {
        return error("STATE_CONFLICT", safe(exception));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> conflict(DataIntegrityViolationException ignored) {
        // Do not expose raw database constraint names or SQL to the browser.
        return error("DATA_CONFLICT", "이미 존재하거나 현재 상태에서 저장할 수 없는 데이터입니다.");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, Object> state(IllegalStateException exception) {
        return error("PROCESSING_FAILED", safe(exception));
    }

    private static Map<String, Object> error(String code, String message) {
        return Map.of("error", code, "message", message);
    }

    private static String safe(Exception exception) {
        if (exception instanceof HttpMessageNotReadableException) return "요청 형식을 확인해 주세요.";
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
