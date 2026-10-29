package com.clinic.exceptions;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 404 Not Found ====================

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNoSuchElement(NoSuchElementException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "ENTITY_NOT_FOUND", ex.getMessage());
    }

    // ==================== 401 & 403 & 429 Security / Auth ====================

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        HttpStatus status = (ex.getReason() == AuthenticationException.FailureReason.ACCOUNT_LOCKED)
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.UNAUTHORIZED;
        return buildResponse(status, ex.getErrorCode(), ex.getUserFacingMessage());
    }

    @ExceptionHandler(InsufficientPermissionException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientPermission(InsufficientPermissionException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getErrorCode(), ex.getUserFacingMessage());
    }

    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    public ResponseEntity<Map<String, Object>> handleSecurityException(Exception ex) {
        String message = (ex instanceof SecurityException && ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : "شما مجوز دسترسی برای انجام این عملیات را ندارید.";
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }

    // ==================== 409 Conflict (Workflow & Concurrency) ====================

    @ExceptionHandler(InvalidWorkflowException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidWorkflow(InvalidWorkflowException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return buildResponse(HttpStatus.CONFLICT, "INVALID_OPERATION", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "اطلاعات این رکورد هم‌زمان توسط کاربر دیگری تغییر یافته است. لطفاً صفحه را بروزرسانی و مجدداً تلاش کنید."
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "DATA_INTEGRITY_CONFLICT",
                "رکورد ارسالی با اطلاعات موجود در سامانه تداخل دارد (احتمال تکرار شناسه یا کلید یکتا)."
        );
    }

    // ==================== 400 Bad Request (Validation & Input) ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        String combinedErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(" | "));
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", combinedErrors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_INPUT", ex.getMessage());
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequestPayload(Exception ex) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "ساختار داده‌های ارسالی یا پارامترهای ورودی ناقص یا نامعتبر است."
        );
    }

    @ExceptionHandler(ClinicException.class)
    public ResponseEntity<Map<String, Object>> handleClinicException(ClinicException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage());
    }

    // ==================== 500 Internal Server Error ====================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "خطای داخلی در پردازش درخواست رخ داده است."
        );
    }

    // ==================== Response Builder ====================

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String errorCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", errorCode);
        body.put("message", (message != null && !message.isBlank()) ? message : "خطای نامشخص.");
        return ResponseEntity.status(status).body(body);
    }
}