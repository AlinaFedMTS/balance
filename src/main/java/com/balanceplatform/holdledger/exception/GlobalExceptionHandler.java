package com.balanceplatform.holdledger.exception;

import com.balanceplatform.holdledger.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates Hold Ledger domain exceptions and Bean Validation failures into the HTTP status
 * codes and error codes mandated by ФТ-BALANCE-001 (FR-005, FR-010, FR-011).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    /**
     * Creates the handler.
     *
     * @param clock clock used to timestamp {@link ErrorResponse}s
     */
    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    /**
     * Maps {@link HoldNotFoundException} to {@code 404 HOLD_NOT_FOUND}.
     *
     * @param ex the exception
     * @return the mapped error response
     */
    @ExceptionHandler(HoldNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleHoldNotFound(HoldNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, HoldNotFoundException.CODE, ex.getMessage());
    }

    /**
     * Maps {@link IdempotencyConflictException} to {@code 409 IDEMPOTENCY_CONFLICT}.
     *
     * @param ex the exception
     * @return the mapped error response
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex) {
        return build(HttpStatus.CONFLICT, IdempotencyConflictException.CODE, ex.getMessage());
    }

    /**
     * Maps {@link ConflictTerminalException} to {@code 409 CONFLICT_TERMINAL}.
     *
     * @param ex the exception
     * @return the mapped error response
     */
    @ExceptionHandler(ConflictTerminalException.class)
    public ResponseEntity<ErrorResponse> handleConflictTerminal(ConflictTerminalException ex) {
        return build(HttpStatus.CONFLICT, ConflictTerminalException.CODE, ex.getMessage());
    }

    /**
     * Maps {@link ThresholdTooLowException} to {@code 400 THRESHOLD_TOO_LOW}.
     *
     * @param ex the exception
     * @return the mapped error response
     */
    @ExceptionHandler(ThresholdTooLowException.class)
    public ResponseEntity<ErrorResponse> handleThresholdTooLow(ThresholdTooLowException ex) {
        return build(HttpStatus.BAD_REQUEST, ThresholdTooLowException.CODE, ex.getMessage());
    }

    /**
     * Maps request body Bean Validation failures to {@code 400 VALIDATION_ERROR}.
     *
     * @param ex the exception
     * @return the mapped error response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    /**
     * Maps request parameter/path variable Bean Validation failures to {@code 400 VALIDATION_ERROR}.
     *
     * @param ex the exception
     * @return the mapped error response
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.builder()
                        .code(code)
                        .message(message)
                        .timestamp(Instant.now(clock))
                        .build());
    }
}
